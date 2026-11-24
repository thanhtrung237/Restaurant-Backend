package com.group.restaurant.repositories;

import com.group.restaurant.models.OptionItem;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OptionItemRepository extends JpaRepository<OptionItem, Long> {

    List<OptionItem> findByOptionGroupId(Long optionGroupId);

    Page<OptionItem> findByOptionGroupId(Long optionGroupId, Pageable pageable);

    Page<OptionItem> findByOptionGroupIdAndNameContainingIgnoreCase(
            Long optionGroupId, String name, Pageable pageable);

    Page<OptionItem> findByNameContainingIgnoreCase(String name, Pageable pageable);

    void deleteByOptionGroupId(Long optionGroupId);
}
