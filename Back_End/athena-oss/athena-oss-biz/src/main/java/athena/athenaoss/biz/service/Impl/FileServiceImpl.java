package athena.athenaoss.biz.service.Impl;

import athena.athenaframework.result.Result;
import athena.athenaoss.biz.service.FileService;
import athena.athenaoss.biz.strategy.FileStrategy;
import com.aliyun.oss.common.utils.HttpHeaders;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class FileServiceImpl implements FileService {

    @Resource
    private FileStrategy fileStrategy;

    private static final String BUCKET_NAME = "xiaoxiaolanfeng-java-ai";

    @Override
    public Result uploadFile(MultipartFile file) {
        // 上传文件
        String url = fileStrategy.uploadFile(file, BUCKET_NAME);

        return Result.ok(url);
    }


}