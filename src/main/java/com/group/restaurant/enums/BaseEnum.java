package com.group.restaurant.enums;

public interface BaseEnum<E extends BaseEnum<E>> {
    int getValue();

}
