package com.proaim.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @date 2019/2/19
 */
@Data
public class Picture implements Serializable {
    private static final long serialVersionUID = -2059032431390697393L;
    //主键
    private Integer id;
    //图片地址
    private String path;
    //备注
    private String remark;
    //添加时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
