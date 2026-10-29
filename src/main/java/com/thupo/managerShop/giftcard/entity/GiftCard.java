package com.thupo.managerShop.giftcard.entity;

import com.thupo.managerShop.auth.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity đại diện cho Thẻ quà tặng (GiftCard).
 * Mã gốc tuyệt đối không được lưu tại đây; chỉ lưu code_hash (HMAC-SHA256) và code_last4.
 */
@Entity
@Table(name = "gift_cards")
public class GiftCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code_hash", nullable = false, unique = true, length = 64)
    private String codeHash;

    @Column(name = "code_last4", nullable = false, length = 4)
    private String codeLast4;

    @Column(name = "initial_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal initialValue;

    @Column(name = "current_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal currentBalance;

    @Column(name = "currency_code", nullable = false, length = 10)
    private String currencyCode = "VND";

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private GiftCardStatus status = GiftCardStatus.ACTIVE;

    @Column(name = "disabled_at")
    private Instant disabledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "giftCard", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("createdAt DESC")
    private List<GiftCardTransaction> transactions = new ArrayList<>();

    public GiftCard() {
    }

    public GiftCard(String codeHash,
                    String codeLast4,
                    BigDecimal initialValue,
                    BigDecimal currentBalance,
                    String currencyCode,
                    Instant expiresAt,
                    Long customerId,
                    String notes,
                    GiftCardStatus status,
                    User createdBy) {
        this.codeHash = codeHash;
        this.codeLast4 = codeLast4;
        this.initialValue = initialValue;
        this.currentBalance = currentBalance;
        this.currencyCode = currencyCode != null ? currencyCode : "VND";
        this.expiresAt = expiresAt;
        this.customerId = customerId;
        this.notes = notes;
        this.status = status != null ? status : GiftCardStatus.ACTIVE;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public void setCodeHash(String codeHash) {
        this.codeHash = codeHash;
    }

    public String getCodeLast4() {
        return codeLast4;
    }

    public void setCodeLast4(String codeLast4) {
        this.codeLast4 = codeLast4;
    }

    public BigDecimal getInitialValue() {
        return initialValue;
    }

    public void setInitialValue(BigDecimal initialValue) {
        this.initialValue = initialValue;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public void setCurrentBalance(BigDecimal currentBalance) {
        this.currentBalance = currentBalance;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public GiftCardStatus getStatus() {
        return status;
    }

    public void setStatus(GiftCardStatus status) {
        this.status = status;
    }

    public Instant getDisabledAt() {
        return disabledAt;
    }

    public void setDisabledAt(Instant disabledAt) {
        this.disabledAt = disabledAt;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<GiftCardTransaction> getTransactions() {
        return transactions;
    }

    public void setTransactions(List<GiftCardTransaction> transactions) {
        this.transactions = transactions;
    }
}
