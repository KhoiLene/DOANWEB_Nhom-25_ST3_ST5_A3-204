package com.shopchain.service;

import com.shopchain.entity.*;
import com.shopchain.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class InventoryOperationsService {
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final StoreRepository storeRepository;
    private final ProductInventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final StockTransferRepository transferRepository;
    private final UserRepository userRepository;

    public InventoryOperationsService(ProductRepository productRepository, WarehouseRepository warehouseRepository,
                                      StoreRepository storeRepository, ProductInventoryRepository inventoryRepository,
                                      InventoryMovementRepository movementRepository, StockTransferRepository transferRepository,
                                      UserRepository userRepository) {
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.storeRepository = storeRepository;
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.transferRepository = transferRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void receiveIntoWarehouse(Long productId, Long warehouseId, Integer quantity,
                                     String batchCode, LocalDate expiry, String note, String username) {
        requirePositive(quantity);
        Product product = productRepository.findById(productId).orElseThrow();
        Warehouse warehouse = warehouseRepository.findForUpdateById(warehouseId).orElseThrow();
        if (!warehouse.isActive()) throw new IllegalArgumentException("Kho này hiện không hoạt động.");
        User actor = userRepository.findByUsername(username).orElseThrow();
        ProductInventory inventory = inventoryRepository.findForUpdateByProductIdAndWarehouseId(productId, warehouseId)
                .orElseGet(() -> ProductInventory.builder().product(product).warehouse(warehouse)
                        .price(product.getPrice()).stockQuantity(0).build());
        inventory.setStockQuantity(inventory.getStockQuantity() + quantity);
        inventoryRepository.save(inventory);
        movementRepository.save(InventoryMovement.builder().product(product).warehouse(warehouse)
                .performedBy(actor).movementType("RECEIVE").quantity(quantity).batchCode(batchCode)
                .expirationDate(expiry).note(note).build());
    }

    @Transactional
    public StockTransfer transferToStore(Long productId, Long warehouseId, Long storeId, Integer quantity,
                                         String note, String username) {
        requirePositive(quantity);
        Warehouse warehouse = warehouseRepository.findForUpdateById(warehouseId).orElseThrow();
        if (!warehouse.isActive()) throw new IllegalArgumentException("Kho này hiện không hoạt động.");
        ProductInventory source = inventoryRepository.findForUpdateByProductIdAndWarehouseId(productId, warehouseId)
                .orElseThrow(() -> new IllegalArgumentException("Sản phẩm chưa có tồn ở kho này."));
        if (source.availableQuantity() < quantity) {
            throw new IllegalArgumentException("Tồn kho không đủ để chuyển.");
        }
        Product product = source.getProduct();
        Store store = storeRepository.findById(storeId).orElseThrow();
        if (!store.isActive()) throw new IllegalArgumentException("Chi nhánh này hiện không hoạt động.");
        User actor = userRepository.findByUsername(username).orElseThrow();

        source.setStockQuantity(source.getStockQuantity() - quantity);
        inventoryRepository.save(source);
        StockTransfer transfer = transferRepository.save(StockTransfer.builder().product(product)
                .sourceWarehouse(warehouse).destinationStore(store).quantity(quantity).status("IN_TRANSIT")
                .createdBy(actor).createdAt(LocalDateTime.now()).build());
        movementRepository.save(InventoryMovement.builder().product(product).warehouse(warehouse)
                .performedBy(actor).movementType("TRANSFER_OUT").quantity(quantity)
                .note("Điều chuyển #" + transfer.getId() + (note == null || note.isBlank() ? "" : ": " + note)).build());
        return transfer;
    }

    @Transactional
    public void receiveTransfer(Long transferId, Long storeId, String username) {
        StockTransfer transfer = transferRepository.findForUpdateById(transferId).orElseThrow();
        if (!transfer.getDestinationStore().getId().equals(storeId)) {
            throw new IllegalArgumentException("Phiếu điều chuyển không thuộc cửa hàng được phân công.");
        }
        if (!"IN_TRANSIT".equals(transfer.getStatus())) {
            throw new IllegalArgumentException("Phiếu này đã được xử lý.");
        }
        Store store = storeRepository.findForUpdateById(storeId).orElseThrow();
        if (!store.isActive()) throw new IllegalArgumentException("Chi nhánh này hiện không hoạt động.");
        User actor = userRepository.findByUsername(username).orElseThrow();
        ProductInventory target = inventoryRepository.findForUpdateByProductIdAndStoreId(
                        transfer.getProduct().getId(), storeId)
                .orElseGet(() -> ProductInventory.builder().product(transfer.getProduct())
                        .store(store).price(transfer.getProduct().getPrice())
                        .stockQuantity(0).build());
        target.setStockQuantity(target.getStockQuantity() + transfer.getQuantity());
        inventoryRepository.save(target);
        transfer.setStatus("RECEIVED");
        transfer.setReceivedBy(actor);
        transfer.setReceivedAt(LocalDateTime.now());
        transferRepository.save(transfer);
        movementRepository.save(InventoryMovement.builder().product(transfer.getProduct())
                .store(transfer.getDestinationStore()).performedBy(actor).movementType("TRANSFER_IN")
                .quantity(transfer.getQuantity()).note("Nhận điều chuyển #" + transfer.getId()).build());
    }

    private void requirePositive(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Số lượng phải lớn hơn 0.");
        }
    }
}
