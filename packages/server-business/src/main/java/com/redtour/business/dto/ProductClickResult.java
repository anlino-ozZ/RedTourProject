package com.redtour.business.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 产品点击排行（按 click_count 倒序）。
 */
@Data
public class ProductClickResult implements Serializable {

    private Long id;
    private String name;
    private Long clickCount;
}
