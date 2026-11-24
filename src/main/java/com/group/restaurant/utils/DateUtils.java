package com.group.restaurant.utils;

import com.group.restaurant.exceptions.InvalidParamException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class DateUtils {
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static String formatDate(LocalDate date) {
        if (date == null) {
            return "";
        }
        return date.format(DATE_FORMATTER);
    }

    public static LocalDate convertStringToLocalDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            throw new InvalidParamException("Chuỗi ngày không được để trống");
        }
        try {
            return LocalDate.parse(dateStr, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new InvalidParamException("Sai định dạng ngày. Định dạng đúng: dd/MM/yyyy");
        }
    }

    public static String formatLocalDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DATE_TIME_FORMATTER);
    }

    public static LocalDateTime convertStringToLocalDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            throw new InvalidParamException("Chuỗi ngày giờ không được để trống");
        }
        try {
            return LocalDateTime.parse(dateTimeStr, DATE_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new InvalidParamException(
                    "Sai định dạng ngày giờ. Định dạng đúng: HH:mm dd/MM/yyyy");
        }
    }
}
