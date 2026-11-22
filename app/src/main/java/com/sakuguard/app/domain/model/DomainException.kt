package com.sakuguard.app.domain.model

sealed class DomainException(message: String) : IllegalStateException(message) {
    class InvalidAmountException(message: String = "Amount must be positive") : DomainException(message)
    class ArithmeticOverflowException(message: String = "Monetary calculation overflow") : DomainException(message)
    class EntityNotFoundException(message: String) : DomainException(message)
    class ArchivedEntityException(message: String) : DomainException(message)
    class InvalidTransferException(message: String) : DomainException(message)
    class InsufficientFreeMoneyException(message: String) : DomainException(message)
    class InsufficientGoalAllocationException(message: String) : DomainException(message)
    class PreTrackingDateException(message: String) : DomainException(message)
    class InvalidTransactionMutationException(message: String) : DomainException(message)
}
