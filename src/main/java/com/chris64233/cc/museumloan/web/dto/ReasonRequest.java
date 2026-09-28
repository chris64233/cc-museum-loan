package com.chris64233.cc.museumloan.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 拒绝 / 取消操作的请求体，均要求填写原因。 */
public record ReasonRequest(

        @NotBlank(message = "原因不能为空")
        @Size(max = 1000, message = "原因长度不能超过 1000")
        String reason) {
}
