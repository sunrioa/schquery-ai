package cn.ling.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档读取策略中心类
 * 根据上传文件的类型自动选择对应的文档读取工具进行内容提取
 * 支持：txt、pdf、excel（xls/xlsx）、word（doc/docx）四种文档类型
 */
@Slf4j
public class DocumentReaderStrategy {

    /** TXT文件类型标识 */
    private static final String TYPE_TXT = "txt";

    /** PDF文件类型标识 */
    private static final String TYPE_PDF = "pdf";

    /** Excel文件类型标识 - xls格式 */
    private static final String TYPE_XLS = "xls";

    /** Excel文件类型标识 - xlsx格式 */
    private static final String TYPE_XLSX = "xlsx";

    /** Word文件类型标识 - doc格式 */
    private static final String TYPE_DOC = "doc";

    /** Word文件类型标识 - docx格式 */
    private static final String TYPE_DOCX = "docx";

    /**
     * 根据文件类型自动选择对应的读取工具提取文档内容
     * 通过文件扩展名识别文档类型，调用相应的工具类进行内容提取
     *
     * @param file 上传的文档文件对象
     * @return 文档中的文本内容；如果文件类型不支持或读取失败返回null
     * @throws RuntimeException 当文件读取过程发生异常时抛出
     */
    public static String read(MultipartFile file) {
        // 参数验证：检查文件是否存在且不为空
        if (file == null || file.isEmpty()) {
            log.warn("文件为空或不存在，无法读取");
            return null;
        }

        String filename = file.getOriginalFilename();
        if (filename == null) {
            log.warn("文件名为空，无法确定文件类型");
            return null;
        }

        log.info("开始读取文档 - 文件名: {}, 大小: {}字节", filename, file.getSize());

        // 获取文件扩展名并转换为小写
        String extension = getFileExtension(filename).toLowerCase();

        // 根据文件类型选择对应的读取策略
        String result = switch (extension) {
            case TYPE_TXT -> {
                log.debug("识别为TXT文件，使用TxtReaderUtil处理");
                yield TxtReaderUtil.read(file);
            }
            case TYPE_PDF -> {
                log.debug("识别为PDF文件，使用PdfReaderUtil处理");
                yield PdfReaderUtil.read(file);
            }
            case TYPE_XLS, TYPE_XLSX -> {
                log.debug("识别为Excel文件，使用ExcelReaderUtil处理");
                yield ExcelReaderUtil.read(file);
            }
            case TYPE_DOC, TYPE_DOCX -> {
                log.debug("识别为Word文件，使用WordReaderUtil处理");
                yield WordReaderUtil.read(file);
            }
            default -> {
                log.warn("不支持的文件类型: {}, 扩展名: {}", filename, extension);
                yield null;
            }
        };

        if (result != null) {
            log.info("文档读取成功 - 文件名: {}, 类型: {}, 内容长度: {}字符",
                    filename, extension, result.length());
        } else {
            log.warn("文档读取失败或返回空内容 - 文件名: {}, 类型: {}", filename, extension);
        }

        return result;
    }

    /**
     * 从文件名中提取文件扩展名
     * 支持带路径的文件名和纯文件名
     *
     * @param filename 文件名（可包含路径）
     * @return 文件扩展名（不包含点号）；如果没有扩展名返回空字符串
     */
    private static String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }

        // 处理路径分隔符，获取纯文件名
        int lastSeparator = Math.max(
                filename.lastIndexOf("/"),
                filename.lastIndexOf("\\")
        );
        String pureFilename = lastSeparator > 0
                ? filename.substring(lastSeparator + 1)
                : filename;

        // 提取扩展名
        int lastDot = pureFilename.lastIndexOf(".");
        if (lastDot > 0 && lastDot < pureFilename.length() - 1) {
            return pureFilename.substring(lastDot + 1);
        }

        return "";
    }

    /**
     * 判断指定的文件类型是否支持
     *
     * @param file 上传的文件对象
     * @return 如果文件类型支持返回true，否则返回false
     */
    public static boolean isSupported(MultipartFile file) {
        if (file == null || file.getOriginalFilename() == null) {
            return false;
        }

        String extension = getFileExtension(file.getOriginalFilename()).toLowerCase();
        return extension.equals(TYPE_TXT)
                || extension.equals(TYPE_PDF)
                || extension.equals(TYPE_XLS)
                || extension.equals(TYPE_XLSX)
                || extension.equals(TYPE_DOC)
                || extension.equals(TYPE_DOCX);
    }

    /**
     * 获取文件的MIME类型描述
     *
     * @param file 上传的文件对象
     * @return MIME类型描述；不支持的文件返回"unknown"
     */
    public static String getFileType(MultipartFile file) {
        if (file == null || file.getOriginalFilename() == null) {
            return "unknown";
        }

        String extension = getFileExtension(file.getOriginalFilename()).toLowerCase();
        return switch (extension) {
            case TYPE_TXT -> "text/plain";
            case TYPE_PDF -> "application/pdf";
            case TYPE_XLS -> "application/vnd.ms-excel";
            case TYPE_XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case TYPE_DOC -> "application/msword";
            case TYPE_DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "unknown";
        };
    }
}
