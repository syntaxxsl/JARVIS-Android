package com.jarvis.assistant.core.common.model

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Failure(val error: AppError) : Result<Nothing>()
    data object Loading : Result<Nothing>()
}

sealed class AppError(val message: String) {
    class Network(message: String = "Ağ hatası") : AppError(message)
    class Auth(message: String = "Kimlik doğrulama hatası") : AppError(message)
    class RateLimit(message: String = "İstek limiti aşıldı") : AppError(message)
    class Unknown(message: String = "Bilinmeyen hata") : AppError(message)
}