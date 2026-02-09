package cn.ling.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Excel文档读取工具类
 * 提供Excel文件（.xls/.xlsx）的文本内容提取功能
 * 基于Apache POI库实现Excel内容读取
 */
@Slf4j
public class ExcelReaderUtil {

    /**
     * 从上传的Excel文件中提取文本内容
     * 提取所有工作表的单元格文本内容，按行列顺序拼接
     *
     * @param file 上传的Excel文件对象
     * @return Excel中的文本内容；如果读取失败返回null
     * @throws RuntimeException 当文件读取过程发生异常时抛出
     */
    public static String read(MultipartFile file) {
        log.debug("开始读取Excel文件 - 文件名: {}, 文件大小: {}字节",
                file.getOriginalFilename(), file.getSize());

        try {
            // 参数验证：检查文件是否存在且不为空
            if (file == null || file.isEmpty()) {
                log.warn("Excel文件为空或不存在，读取失败");
                return null;
            }

            // 使用POI Workbook读取Excel内容
            StringBuilder content = new StringBuilder();

            try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {

                int sheetCount = workbook.getNumberOfSheets();
                log.debug("Excel文档加载成功 - 工作表数量: {}", sheetCount);

                // 遍历所有工作表
                for (int sheetIndex = 0; sheetIndex < sheetCount; sheetIndex++) {
                    var sheet = workbook.getSheetAt(sheetIndex);
                    String sheetName = sheet.getSheetName();

                    log.debug("开始处理工作表 - 索引: {}, 名称: {}", sheetIndex, sheetName);

                    // 添加工作表名称标识
                    if (content.length() > 0) {
                        content.append("\n\n");
                    }
                    content.append("【工作表: ").append(sheetName).append("】\n");

                    // 遍历所有行
                    for (var row : sheet) {
                        StringBuilder rowContent = new StringBuilder();

                        // 遍历所有单元格
                        for (var cell : row) {
                            if (rowContent.length() > 0) {
                                rowContent.append("\t");
                            }

                            // 获取单元格字符串值
                            String cellValue = getCellValueAsString(cell);
                            rowContent.append(cellValue);
                        }

                        // 添加行内容
                        if (rowContent.length() > 0) {
                            content.append(rowContent).append("\n");
                        }
                    }
                }

                log.info("Excel文件读取成功 - 文件名: {}, 工作表数: {}, 提取文本长度: {}字符",
                        file.getOriginalFilename(), sheetCount, content.length());

                return content.toString();
            }

        } catch (IOException e) {
            log.error("Excel文件读取失败 - 文件名: {}, 错误信息: {}",
                    file.getOriginalFilename(), e.getMessage(), e);
            throw new RuntimeException("Excel文件读取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将单元格值转换为字符串
     * 处理不同类型的单元格数据（字符串、数字、日期、布尔等）
     *
     * @param cell POI单元格对象
     * @return 单元格的字符串表示
     */
    private static String getCellValueAsString(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) {
            return "";
        }

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                // 避免整数显示为小数
                double value = cell.getNumericCellValue();
                if (value == (long) value) {
                    yield String.valueOf((long) value);
                } else {
                    yield String.valueOf(value);
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            case BLANK -> "";
            default -> "";
        };
    }
}
