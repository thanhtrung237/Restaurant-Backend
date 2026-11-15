package com.group.restaurant;
import com.group.restaurant.models.*;
import com.group.restaurant.repositories.*;
import com.group.restaurant.services.guest.GuestAdmissionService;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class GuestAdmissionConcurrencyTest {
    @Autowired RestaurantRepository restaurants;
    @Autowired BranchRepository branches;
    @Autowired DiningTableRepository tables;
    @Autowired TableSessionRepository sessions;
    @Autowired GuestAdmissionService admission;
    @Autowired PlatformTransactionManager transactionManager;
    @Test void simultaneousRequestsCreateOnlyOneSession() throws Exception {
        var tx = new TransactionTemplate(transactionManager);
        Long[] ids = tx.execute(status -> {
            Restaurant r = new Restaurant(); r.setCode(UUID.randomUUID().toString()); r.setName("Concurrent"); restaurants.save(r);
            Branch b = new Branch(); b.setRestaurant(r); b.setCode("C"); b.setName("Concurrent branch"); branches.save(b);
            DiningTable table = DiningTable.builder().branch(b).name("Concurrent table").qrCode("CONCURRENT").build(); tables.save(table);
            return new Long[]{b.getId(),table.getId()};
        });
        var request = new GuestAdmissionService.AccessRequest(ids[0],"CONCURRENT",2,null,null);
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Long> attempt = () -> {
                gate.await();
                return admission.enter(request, UUID.randomUUID().toString()).session().getId();
            };
            var first=pool.submit(attempt); var second=pool.submit(attempt); gate.countDown();
            assertThat(first.get(10,TimeUnit.SECONDS)).isEqualTo(second.get(10,TimeUnit.SECONDS));
        }
        Integer sessionCount = tx.execute(status -> sessions.findByTableId(ids[1]).size());
        assertThat(sessionCount).isEqualTo(1);
    }
}
