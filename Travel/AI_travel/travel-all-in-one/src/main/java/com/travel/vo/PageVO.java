package com.travel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分页响应")
public class PageVO<T> {
    @Schema(description = "数据列表")
    private List<T> records;

    @Schema(description = "总条数", example = "156")
    private Long total;

    @Schema(description = "当前页", example = "1")
    private Long page;

    @Schema(description = "每页条数", example = "20")
    private Long size;

    @Schema(description = "总页数", example = "8")
    private Long pages;

    /**
    * 构造分页结果
    */
    public static <T> PageVO<T> of(List<T> records, Long total, Long page, Long size) {
      PageVO<T> vo = new PageVO<>();
      vo.setRecords(records);
      vo.setTotal(total);
      vo.setPage(page);
      vo.setSize(size);
      vo.setPages((total + size - 1) / size);  // 向上取整
      return vo;
    }
}