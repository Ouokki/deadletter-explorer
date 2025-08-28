package com.dle.dlq.model;

public record MaskOptions(Integer keepFirst, Integer keepLast, String pad, String fixed) {
}