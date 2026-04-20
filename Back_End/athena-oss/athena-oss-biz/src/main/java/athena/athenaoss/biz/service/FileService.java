package athena.athenaoss.biz.service;

import athena.athenaframework.result.Result;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {
    Result uploadFile(MultipartFile file);
}
