package com.gait.gaitproject.service.chat

/**
 * 토큰 수 추정 유틸리티
 * 한글/영문 혼합 기준 대략 1토큰 ≈ 4자 (보수적 추정)
 */
object TokenEstimator {
    /**
     * 텍스트의 대략적인 토큰 수 추정
     */
    fun estimate(text: String): Int {
        if (text.isBlank()) return 0
        // 한글/영문 혼합 기준 보수적 추정: 1토큰 ≈ 4자
        return (text.length / 4.0).toInt().coerceAtLeast(1)
    }
    
    /**
     * 여러 텍스트의 총 토큰 수 추정
     */
    fun estimateTotal(texts: List<String>): Int {
        return texts.sumOf { estimate(it) }
    }
}


