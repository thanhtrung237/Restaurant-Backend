package com.group.restaurant.repositories;

import com.group.restaurant.models.OptionItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OptionItemRepository extends JpaRepository<OptionItem, Long> {

    List<OptionItem> findByOptionGroupId(Long optionGroupId);

    Page<OptionItem> findByOptionGroupId(Long optionGroupId, Pageable pageable);

    Page<OptionItem> findByOptionGroupIdAndNameContainingIgnoreCase(Long optionGroupId, String name, Pageable pageable);

    Page<OptionItem> findByNameContainingIgnoreCase(String name, Pageable pageable);

    void deleteByOptionGroupId(Long optionGroupId);
}
