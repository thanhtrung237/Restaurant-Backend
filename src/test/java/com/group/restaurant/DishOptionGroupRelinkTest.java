package com.group.restaurant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.group.restaurant.dtos.request.dishOptionGroup.DishOptionGroupAddRequest;
import com.group.restaurant.enums.CategoryStatus;
import com.group.restaurant.enums.DishStatus;
import com.group.restaurant.enums.SelectionType;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.models.Category;
import com.group.restaurant.models.Dish;
import com.group.restaurant.models.OptionGroup;
import com.group.restaurant.models.Restaurant;
import com.group.restaurant.repositories.DishOptionGroupRepository;
import com.group.restaurant.services.DishOptionGroupService;
import com.group.restaurant.utils.TenantGuard;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class DishOptionGroupRelinkTest {

    @Autowired EntityManager entities;
    @Autowired DishOptionGroupService service;
    @Autowired DishOptionGroupRepository links;
    @MockBean TenantGuard tenantGuard;

    @Test
    void relinkingRestoresTheExistingRowAndKeepsTheActiveLinkUnique() {
        Restaurant restaurant = new Restaurant();
        restaurant.setCode("RELINK-TEST");
        restaurant.setName("Nhà hàng kiểm thử");
        entities.persist(restaurant);
        Category category =
                Category.builder()
                        .restaurant(restaurant)
                        .name("Khai vị")
                        .status(CategoryStatus.ACTIVE)
                        .build();
        entities.persist(category);
        Dish dish =
                Dish.builder()
                        .restaurant(restaurant)
                        .category(category)
                        .name("Tàu hủ")
                        .basePrice(BigDecimal.valueOf(15000))
                        .soldByWeight(false)
                        .status(DishStatus.ACTIVE)
                        .build();
        entities.persist(dish);
        OptionGroup group =
                OptionGroup.builder()
                        .restaurant(restaurant)
                        .name("Topping")
                        .selectionType(SelectionType.OPTIONAL)
                        .maxSelect(3)
                        .build();
        entities.persist(group);

        var request = new DishOptionGroupAddRequest();
        request.setDishId(dish.getId());
        request.setOptionGroupId(group.getId());
        var original = service.linkDishToOptionGroup(request);

        service.unlinkDishFromOptionGroup(original.getId());
        entities.flush();
        entities.clear();
        assertThat(service.getDishOptionGroupsByDish(dish.getId())).isEmpty();

        var restored = service.linkDishToOptionGroup(request);
        entities.flush();
        entities.clear();

        assertThat(restored.getId()).isEqualTo(original.getId());
        assertThat(service.getDishOptionGroupsByDish(dish.getId())).hasSize(1);
        var row = links.findById(original.getId()).orElseThrow();
        assertThat(row.getIsDeleted()).isFalse();
        assertThat(row.getDeletedAt()).isNull();
        assertThat(row.getDeletedBy()).isNull();
        assertThatThrownBy(() -> service.linkDishToOptionGroup(request))
                .isInstanceOf(ConflictException.class);
    }
}
