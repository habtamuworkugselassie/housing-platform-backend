package com.housingplatform.construction.service;

import com.housingplatform.construction.domain.BillOfQuantities;
import com.housingplatform.construction.domain.BoQItem;
import com.housingplatform.construction.dto.BillOfQuantitiesRequest;
import com.housingplatform.construction.dto.BillOfQuantitiesResponse;
import com.housingplatform.construction.dto.BoQItemRequest;
import com.housingplatform.construction.repository.BillOfQuantitiesRepository;
import com.housingplatform.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class BillOfQuantitiesServiceImpl implements BillOfQuantitiesService {
    
    private final BillOfQuantitiesRepository boqRepository;
    private final BillOfQuantitiesMapper boqMapper;
    
    @Override
    public BillOfQuantitiesResponse createBoQ(BillOfQuantitiesRequest request) {
        BillOfQuantities boq = boqMapper.toEntity(request);
        boq.setStatus(BillOfQuantities.BoQStatus.DRAFT);
        
        // Calculate total cost
        BigDecimal totalCost = request.getItems().stream()
                .map(item -> item.getQuantity().multiply(item.getUnitPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boq.setTotalEstimatedCost(totalCost);
        
        // Create items
        for (int i = 0; i < request.getItems().size(); i++) {
            BoQItemRequest itemRequest = request.getItems().get(i);
            BoQItem item = boqMapper.toItemEntity(itemRequest);
            item.setBillOfQuantities(boq);
            item.setTotalPrice(itemRequest.getQuantity().multiply(itemRequest.getUnitPrice()));
            item.setSequence(i + 1);
            boq.getItems().add(item);
        }
        
        BillOfQuantities saved = boqRepository.save(boq);
        return boqMapper.toResponse(saved);
    }
    
    @Override
    @Transactional(readOnly = true)
    public BillOfQuantitiesResponse getBoQById(UUID id) {
        BillOfQuantities boq = boqRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BillOfQuantities", id));
        return boqMapper.toResponse(boq);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<BillOfQuantitiesResponse> getAllBoQs(UUID propertyId, UUID projectId) {
        List<BillOfQuantities> boqs;
        
        if (propertyId != null) {
            boqs = boqRepository.findByPropertyId(propertyId);
        } else if (projectId != null) {
            boqs = boqRepository.findByProjectId(projectId);
        } else {
            boqs = boqRepository.findAll();
        }
        
        return boqs.stream()
                .map(boqMapper::toResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    public BillOfQuantitiesResponse updateBoQ(UUID id, BillOfQuantitiesRequest request) {
        BillOfQuantities boq = boqRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BillOfQuantities", id));
        
        // Clear existing items
        boq.getItems().clear();
        
        // Update basic fields
        boq.setName(request.getName());
        boq.setDescription(request.getDescription());
        boq.setPropertyId(request.getPropertyId());
        boq.setProjectId(request.getProjectId());
        
        // Recalculate total and add items
        BigDecimal totalCost = BigDecimal.ZERO;
        for (int i = 0; i < request.getItems().size(); i++) {
            BoQItemRequest itemRequest = request.getItems().get(i);
            BoQItem item = boqMapper.toItemEntity(itemRequest);
            item.setBillOfQuantities(boq);
            item.setTotalPrice(itemRequest.getQuantity().multiply(itemRequest.getUnitPrice()));
            item.setSequence(i + 1);
            boq.getItems().add(item);
            totalCost = totalCost.add(item.getTotalPrice());
        }
        boq.setTotalEstimatedCost(totalCost);
        
        BillOfQuantities updated = boqRepository.save(boq);
        return boqMapper.toResponse(updated);
    }
    
    @Override
    public void deleteBoQ(UUID id) {
        BillOfQuantities boq = boqRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BillOfQuantities", id));
        boqRepository.delete(boq);
    }
}
