package com.pbtservers.pbtmetagpt

object SecretPatterns {
    fun contains(text: String) = Regex("sk-[A-Za-z0-9_-]{20,}|gsk_[A-Za-z0-9]{20,}|hf_[A-Za-z0-9]{20,}|(?i)bearer\\s+[A-Za-z0-9_.-]{16,}|-----BEGIN .*PRIVATE KEY").containsMatchIn(text)
}
