package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.invoice.InvoiceCreateRequest;
import com.group.restaurant.dtos.response.invoice.InvoiceListResponse;
import com.group.restaurant.dtos.response.invoice.InvoiceResponse;
import com.group.restaurant.dtos.response.invoice.QrCodeResponse;
import com.group.restaurant.enums.DiscountType;
import com.group.restaurant.enums.DiningTableStatus;
import com.group.restaurant.enums.InvoiceStatus;
import com.group.restaurant.enums.OrderDetailStatus;
import com.group.restaurant.enums.TableSessionStatus;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.InvalidParamException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.InvoiceMapper;
import com.group.restaurant.models.*;
import com.group.restaurant.repositories.*;
import com.group.restaurant.services.InvoiceService;
import com.group.restaurant.services.einvoice.EInvoiceException;
import com.group.restaurant.services.einvoice.EInvoiceResult;
import com.group.restaurant.services.einvoice.InvoiceProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final BranchRepository branchRepository;
    private final TableSessionRepository tableSessionRepository;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final ManualDiscountRepository manualDiscountRepository;
    private final PaymentRepository paymentRepository;
    private final DiningTableRepository diningTableRepository;
    private final InvoiceMapper invoiceMapper;
    private final InvoiceProvider invoiceProvider;

    @Override
    public InvoiceResponse createInvoice(InvoiceCreateRequest request) {
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy chi nhánh với ID: " + request.getBranchId()));

        TableSession session = tableSessionRepository.findById(request.getTableSessionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy phiên làm việc với ID: " + request.getTableSessionId()));

        // Phiên phải ở WAITING_FOR_PAY mới được tạo hóa đơn
        if (session.getStatus() != TableSessionStatus.WAITING_FOR_PAY) {
            throw new ConflictException(
                    "Phiên bàn phải ở trạng thái CHỜ THANH TOÁN mới có thể lập hóa đơn. Trạng thái hiện tại: "
                            + session.getStatus());
        }

        // Nếu hóa đơn cho phiên làm việc này đã tồn tại, trả về hóa đơn đó (Idempotent)
        Optional<Invoice> existingOpt = invoiceRepository.findByTableSessionId(request.getTableSessionId());
        if (existingOpt.isPresent() && existingOpt.get().getStatus() != InvoiceStatus.CANCELLED) {
            return buildInvoiceResponse(existingOpt.get());
        }

        // Lấy tất cả dòng món của phiên
        List<OrderDetail> details = orderDetailRepository.findByTableSessionId(session.getId());

        if (details.isEmpty()) {
            throw new InvalidParamException(
                    "Phiên làm việc chưa có món ăn nào để lập hóa đơn");
        }

        // Chặn nếu còn dòng CHỜ CÂN (FR-PAY-01)
        boolean hasPendingWeight = details.stream()
                .anyMatch(d -> d.getStatus() == OrderDetailStatus.PENDING_WEIGHT);
        if (hasPendingWeight) {
            throw new ConflictException(
                    "Không thể lập hóa đơn khi còn món đang chờ cân. Vui lòng cân và xác nhận tất cả các món bán theo cân trước.");
        }

        //  Bước 1: subTotal = Σ(đơn giá × số lượng + option extras) chỉ của dòng TÍNH TIỀN
        // Dòng CANCELLED "lỗi nhà hàng" không tính, dòng CANCELLED "khách đổi ý" vẫn tính
        // price đã được tính = (unitPrice + optionExtra) * qty trong buildDetail (BR-07, BR-13)
        BigDecimal subTotal = details.stream()
                .filter(d -> shouldChargeDetail(d))
                .map(d -> {
                    if (d.getPrice() != null) return d.getPrice();
                    BigDecimal qty = BigDecimal.valueOf(
                            d.getWeightKg() != null ? d.getWeightKg().doubleValue()
                                    : (d.getQuantity() != null ? d.getQuantity() : 1));
                    return d.getPriceAtOrderTime() != null
                            ? d.getPriceAtOrderTime().multiply(qty)
                            : BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Bước 2: Khuyến mãi tự động — tạm thời = 0 (implement sau khi có Promotion flow)
        BigDecimal totalPromotionDiscount = BigDecimal.ZERO;

        // Bước 3: Chiết khấu thủ công từ ManualDiscount gắn với invoice (nếu có)
        // ManualDiscount được tạo trước khi lập hóa đơn và gắn qua invoice
        // Ở đây tổng hợp tất cả manual discount của các order trong phiên
        List<Order> orders = orderRepository.findByTableSessionId(session.getId());
        BigDecimal totalManualDiscount = orders.stream()
                .flatMap(o -> manualDiscountRepository.findByOrderId(o.getId()).stream())
                .map(md -> calculateDiscountAmount(md, subTotal.subtract(totalPromotionDiscount)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Đảm bảo chiết khấu không vượt quá tiền hàng sau khuyến mãi
        BigDecimal afterPromo = subTotal.subtract(totalPromotionDiscount);
        if (totalManualDiscount.compareTo(afterPromo) > 0) {
            totalManualDiscount = afterPromo;
        }

        //  Bước 4: Phí dịch vụ từ snapshot phiên (BR-08)
        BigDecimal afterDiscount = afterPromo.subtract(totalManualDiscount);
        BigDecimal serviceFeeRate = session.getServiceFeeRateAtOpen() != null
                ? session.getServiceFeeRateAtOpen() : BigDecimal.ZERO;
        BigDecimal serviceFee = afterDiscount
                .multiply(serviceFeeRate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        // Bước 5: VAT từ snapshot phiên (BR-08)
        BigDecimal afterServiceFee = afterDiscount.add(serviceFee);
        BigDecimal vatRate = session.getVatRateAtOpen() != null
                ? session.getVatRateAtOpen() : BigDecimal.ZERO;
        BigDecimal vatAmount = afterServiceFee
                .multiply(vatRate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        // Tổng cuối cùng
        BigDecimal grandTotal = afterServiceFee.add(vatAmount);
        if (grandTotal.compareTo(BigDecimal.ZERO) < 0) {
            grandTotal = BigDecimal.ZERO;
        }

        //  Số hóa đơn đánh riêng theo chi nhánh
        String invoiceNumber = generateInvoiceNumber(branch);

        Order primaryOrder = orders.isEmpty() ? null : orders.get(orders.size() - 1);

        Invoice invoice = Invoice.builder()
                .branch(branch)
                .tableSession(session)
                .order(primaryOrder)
                .invoiceNumber(invoiceNumber)
                .invoiceNote(request.getInvoiceNote())
                .subTotalBeforeDiscount(subTotal)
                .totalPromotionDiscount(totalPromotionDiscount)
                .totalManualDiscount(totalManualDiscount)
                .serviceFee(serviceFee)
                .vatAmount(vatAmount)
                .grandTotal(grandTotal)
                .status(InvoiceStatus.PENDING)
                .build();

        Invoice savedInvoice = invoiceRepository.save(invoice);

        // Gắn invoice vào các ManualDiscount đã tạo cho các order
        orders.stream()
                .flatMap(o -> manualDiscountRepository.findByOrderId(o.getId()).stream())
                .filter(md -> md.getInvoice() == null)
                .forEach(md -> {
                    md.setInvoice(savedInvoice);
                    manualDiscountRepository.save(md);
                });

        return buildInvoiceResponse(savedInvoice);
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceById(Long id) {
        return buildInvoiceResponse(findInvoiceById(id));
    }

    @Override
    @Transactional
    public InvoiceResponse getInvoiceByTableSession(Long tableSessionId) {
        Optional<Invoice> existingInvoice = invoiceRepository.findByTableSessionId(tableSessionId);
        if (existingInvoice.isPresent()) {
            return buildInvoiceResponse(existingInvoice.get());
        }

        // Tự động tạo hóa đơn nếu phiên bàn đã có món và đang phục vụ / chờ thanh toán
        TableSession session = tableSessionRepository.findById(tableSessionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy phiên làm việc ID: " + tableSessionId));

        List<OrderDetail> details = orderDetailRepository.findByTableSessionId(session.getId());
        if (!details.isEmpty() && (session.getStatus() == TableSessionStatus.WAITING_FOR_PAY
                || session.getStatus() == TableSessionStatus.SERVING)) {
            if (session.getStatus() == TableSessionStatus.SERVING) {
                session.setStatus(TableSessionStatus.WAITING_FOR_PAY);
                tableSessionRepository.save(session);
            }
            InvoiceCreateRequest req = new InvoiceCreateRequest();
            req.setBranchId(session.getTable().getBranch().getId());
            req.setTableSessionId(tableSessionId);
            return createInvoice(req);
        }

        throw new ResourceNotFoundException(
                "Không tìm thấy hóa đơn cho phiên làm việc ID: " + tableSessionId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceListResponse> getInvoicesByBranch(Long branchId, InvoiceStatus status) {
        if (!branchRepository.existsById(branchId)) {
            throw new ResourceNotFoundException("Không tìm thấy chi nhánh với ID: " + branchId);
        }
        List<Invoice> invoices = (status != null)
                ? invoiceRepository.findByBranchIdAndStatus(branchId, status)
                : invoiceRepository.findByBranchId(branchId);

        return invoices.stream()
                .map(invoiceMapper::toListResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void cancelInvoice(Long id) {
        Invoice invoice = findInvoiceById(id);
        // BR-09: Hóa đơn đã phát hành là BẤT BIẾN — cả PAID lẫn ISSUED đều không hủy được
        if (invoice.getStatus() == InvoiceStatus.PAID || invoice.getStatus() == InvoiceStatus.ISSUED) {
            throw new ConflictException("Không thể hủy hóa đơn đã thanh toán hoặc đã phát hành điện tử (BR-09)");
        }
        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoiceRepository.save(invoice);
    }

    // Xóa hóa đơn bị cấm — chỉ cho cancel
    // deleteInvoice() đã bị xóa khỏi InvoiceService interface

  
    @Override
    public InvoiceResponse issueEInvoice(Long invoiceId) {
        Invoice invoice = findInvoiceById(invoiceId);

        // Chỉ cho phát hành khi đã PAID
        if (invoice.getStatus() != InvoiceStatus.PAID) {
            throw new ConflictException(
                "Chỉ có thể phát hành hóa đơn điện tử cho hóa đơn đã thanh toán đủ. " +
                "Trạng thái hiện tại: " + invoice.getStatus());
        }

        // Resolve mã số thuế theo : ưu tiên chi nhánh, nếu không có thì dùng nhà hàng
        Branch branch = invoice.getBranch();
        String taxCode = (branch.getTaxCode() != null && !branch.getTaxCode().isBlank())
                ? branch.getTaxCode()
                : branch.getRestaurant().getTaxCode();

        // Tăng attempt count trước khi gọi provider (để ghi nhận dù thành công hay thất bại)
        invoice.setEInvoiceAttemptCount(invoice.getEInvoiceAttemptCount() + 1);
        invoiceRepository.saveAndFlush(invoice);

        // Gọi provider — có thể ném EInvoiceException nếu thất bại
        EInvoiceResult result;
        try {
            result = invoiceProvider.issue(invoice);
        } catch (EInvoiceException ex) {
            // FR-PAY-04: Phát hành thất bại → CHẶN đóng bàn, Thu ngân thử lại
            log.error("[FR-PAY-04] Phát hành HĐĐT thất bại cho invoice #{} qua provider '{}': {}",
                invoiceId, ex.getProviderName(), ex.getMessage());
            throw new ConflictException(
                "Phát hành hóa đơn điện tử thất bại (lần thử #" + invoice.getEInvoiceAttemptCount() + "): "
                + ex.getMessage() + ". Vui lòng thử lại.");
        }

        // Phát hành thành công — cập nhật invoice với đầy đủ thông tin HĐĐT
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setEInvoiceRef(result.getRef());
        invoice.setEInvoiceIssuedAt(result.getIssuedAt() != null ? result.getIssuedAt() : LocalDateTime.now());
        invoice.setTaxCodeUsed(taxCode);
        invoice.setEInvoiceProvider(result.getProviderName());
        // saveAndFlush đảm bảo các field HĐĐT được ghi xuống DB ngay trong transaction này
        invoiceRepository.saveAndFlush(invoice);

        // Đóng session và bàn sau khi HĐĐT phát hành xong (FR-PAY-04: thành công mới CLOSED)
        TableSession session = invoice.getTableSession();
        if (session != null
                && session.getStatus() != TableSessionStatus.CLOSED
                && session.getStatus() != TableSessionStatus.CANCELLED) {
            session.setStatus(TableSessionStatus.CLOSED);
            session.setClosedAt(LocalDateTime.now());
            tableSessionRepository.save(session);

            DiningTable table = session.getTable();
            if (table != null) {
                table.setStatus(DiningTableStatus.AVAILABLE);
                diningTableRepository.save(table);
            }
        }

        log.info("[FR-PAY-04] Phát hành HĐĐT thành công: invoice #{} → ref='{}', provider='{}', taxCode='{}'",
            invoiceId, result.getRef(), result.getProviderName(), taxCode);

        // Re-fetch từ DB sau saveAndFlush để response trả về đúng data đã persist
        Invoice refreshed = findInvoiceById(invoiceId);
        return buildInvoiceResponse(refreshed);
    }

    @Override
    @Transactional(readOnly = true)
    public QrCodeResponse generateQrCode(Long invoiceId) {
        Invoice invoice = findInvoiceById(invoiceId);

        // Tính số tiền còn lại
        BigDecimal totalPaid = paymentRepository.sumAmountByInvoiceId(invoiceId);
        if (totalPaid == null) totalPaid = BigDecimal.ZERO;
        BigDecimal remainingAmount = invoice.getGrandTotal().subtract(totalPaid);
        if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0 || invoice.getStatus() == InvoiceStatus.PAID || invoice.getStatus() == InvoiceStatus.ISSUED) {
            return QrCodeResponse.builder()
                    .invoiceId(invoice.getId())
                    .invoiceNumber(invoice.getInvoiceNumber())
                    .remainingAmount(BigDecimal.ZERO)
                    .grandTotal(invoice.getGrandTotal())
                    .totalPaid(totalPaid)
                    .bankName("")
                    .accountNo("")
                    .accountName("")
                    .qrImageUrl("")
                    .transferContent("")
                    .build();
        }

        // Lấy thông tin tài khoản ngân hàng của chi nhánh (fallback nếu chưa cấu hình)
        Branch branch = invoice.getBranch();
        String rawAccountNo = (branch != null && branch.getBankAccountNo() != null && !branch.getBankAccountNo().isBlank())
                ? branch.getBankAccountNo()
                : "0071000988888";
        String bankName = (branch != null && branch.getBankName() != null && !branch.getBankName().isBlank())
                ? branch.getBankName()
                : "Vietcombank";
        String accountName = (branch != null && branch.getName() != null && !branch.getName().isBlank())
                ? branch.getName()
                : "Nhà hàng Phở Việt";

        String bankAccountNo = rawAccountNo.replaceAll("\\s+", "");

        // Nội dung chuyển khoản (bỏ dấu gạch để gọn)
        String transferContent = "TT " + invoice.getInvoiceNumber().replace("-", "");

        // Sinh URL QR VietQR public API
        String qrImageUrl = buildVietQrUrl(bankName, bankAccountNo, remainingAmount,
                transferContent, accountName);

        return QrCodeResponse.builder()
                .invoiceId(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .remainingAmount(remainingAmount)
                .grandTotal(invoice.getGrandTotal())
                .totalPaid(totalPaid)
                .bankName(bankName)
                .accountNo(bankAccountNo)
                .accountName(accountName)
                .qrImageUrl(qrImageUrl)
                .transferContent(transferContent)
                .build();
    }

    @Override
    @Transactional
    public QrCodeResponse generateQrCodeByTableSession(Long tableSessionId) {
        Optional<Invoice> invoiceOpt = invoiceRepository.findByTableSessionId(tableSessionId);
        if (invoiceOpt.isPresent()) {
            return generateQrCode(invoiceOpt.get().getId());
        }

        // Tự động tạo hóa đơn cho phiên bàn nếu phiên bàn có món
        TableSession session = tableSessionRepository.findById(tableSessionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy phiên bàn #" + tableSessionId));

        List<OrderDetail> details = orderDetailRepository.findByTableSessionId(session.getId());
        if (!details.isEmpty() && (session.getStatus() == TableSessionStatus.WAITING_FOR_PAY
                || session.getStatus() == TableSessionStatus.SERVING)) {
            if (session.getStatus() == TableSessionStatus.SERVING) {
                session.setStatus(TableSessionStatus.WAITING_FOR_PAY);
                tableSessionRepository.save(session);
            }
            InvoiceCreateRequest req = new InvoiceCreateRequest();
            req.setBranchId(session.getTable().getBranch().getId());
            req.setTableSessionId(tableSessionId);
            InvoiceResponse created = createInvoice(req);
            return generateQrCode(created.getId());
        }

        throw new ResourceNotFoundException(
                "Không tìm thấy hóa đơn cho phiên bàn #" + tableSessionId);
    }

    private String buildVietQrUrl(String bankName, String accountNo,
            BigDecimal amount, String addInfo, String accountName) {
        String bankId = normalizeBankId(bankName);
        String baseAccountNo = (accountNo != null) ? accountNo : "0000000000";

        return "https://img.vietqr.io/image/"
                + bankId + "-" + baseAccountNo + "-compact2.png"
                + "?amount=" + amount.longValue()
                + "&addInfo=" + encode(addInfo)
                + "&accountName=" + encode(accountName);
    }

   
    private String normalizeBankId(String bankName) {
        if (bankName == null) return "VCB";
        return switch (bankName.toUpperCase().trim()) {
            case "VCB", "VIETCOMBANK" -> "VCB";
            case "TCB", "TECHCOMBANK" -> "TCB";
            case "MB", "MBBANK" -> "MB";
            case "ACB" -> "ACB";
            case "VPB", "VPBANK" -> "VPB";
            case "BIDV" -> "BIDV";
            case "CTG", "VIETINBANK" -> "CTG";
            case "AGR", "AGRIBANK" -> "AGR";
            case "TPB", "TPBANK" -> "TPB";
            case "STB", "SACOMBANK" -> "STB";
            case "OCB" -> "OCB";
            case "MSB" -> "MSB";
            case "SHB" -> "SHB";
            default -> bankName.toUpperCase();
        };
    }

    private String encode(String value) {
        if (value == null) return "";
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    private Invoice findInvoiceById(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy hóa đơn với ID: " + id));
    }

    /**
     * Xác định dòng món có tính tiền không.
     * Dựa trực tiếp vào flag isBillable được set khi hủy:
     *  - cancelBeforeKitchen → isBillable = false → không tính
     *  - cancelAfterKitchen lỗi nhà hàng → isBillable = false → không tính
     *  - cancelAfterKitchen khách đổi ý → isBillable = true → vẫn tính (BR-24)
     */
    private boolean shouldChargeDetail(OrderDetail d) {
        if (d.getStatus() != OrderDetailStatus.CANCELLED) {
            return true;
        }
        return Boolean.TRUE.equals(d.getIsBillable());
    }

    /**
     *  Số hóa đơn riêng theo chi nhánh
     */
    private String generateInvoiceNumber(Branch branch) {
        String branchCode = branch.getCode() != null ? branch.getCode().toUpperCase() : "B" + branch.getId();
        String date = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        long seq = invoiceRepository.countByBranchId(branch.getId()) + 1;
        String invoiceNumber = branchCode + "-" + date + "-" + String.format("%04d", seq);

        // Đảm bảo không trùng (edge case)
        while (invoiceRepository.existsByInvoiceNumber(invoiceNumber)) {
            seq++;
            invoiceNumber = branchCode + "-" + date + "-" + String.format("%04d", seq);
        }
        return invoiceNumber;
    }

    /**
     * Tính số tiền chiết khấu từ ManualDiscount
     */
    private BigDecimal calculateDiscountAmount(ManualDiscount md, BigDecimal base) {
        if (md.getValue() == null) return BigDecimal.ZERO;
        if (md.getType() == DiscountType.PERCENTAGE) {
            return base.multiply(md.getValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        return md.getValue(); // FIXED_AMOUNT
    }

    private InvoiceResponse buildInvoiceResponse(Invoice invoice) {
        InvoiceResponse response = invoiceMapper.toResponse(invoice);

        BigDecimal totalPaid = paymentRepository.sumAmountByInvoiceId(invoice.getId());
        if (totalPaid == null) totalPaid = BigDecimal.ZERO;

        BigDecimal remainingAmount = invoice.getGrandTotal().subtract(totalPaid);
        if (remainingAmount.compareTo(BigDecimal.ZERO) < 0) remainingAmount = BigDecimal.ZERO;

        response.setTotalPaid(totalPaid);
        response.setRemainingAmount(remainingAmount);
        return response;
    }
}
