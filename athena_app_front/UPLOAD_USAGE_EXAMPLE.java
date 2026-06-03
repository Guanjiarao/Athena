// ========== 使用示例 ==========

import com.example.athena.utils.SimpleUploadUtil;
import java.io.File;

public class UploadUsageExample {

    /**
     * 示例：上传图片文件
     */
    public void uploadImage() {
        // 1. 准备要上传的文件
        File imageFile = new File("/sdcard/DCIM/photo.jpg");
        
        // 2. 服务器上传地址
        String serverUrl = "http://your-domain.com/upload";
        
        // 3. 调用上传方法
        SimpleUploadUtil.uploadFile(imageFile, serverUrl, new SimpleUploadUtil.UploadCallback() {
            @Override
            public void onSuccess(String result) {
                // 上传成功，已在主线程回调
                // result 是服务器返回的 JSON 字符串
                System.out.println("上传成功: " + result);
                // 可以在这里更新 UI，例如显示 Toast
            }

            @Override
            public void onFailure(String errorMsg) {
                // 上传失败，已在主线程回调
                System.out.println("上传失败: " + errorMsg);
                // 可以在这里显示错误提示
            }
        });
    }

    /**
     * 示例：使用文件路径上传
     */
    public void uploadByPath() {
        String filePath = "/sdcard/DCIM/photo.jpg";
        String serverUrl = "http://your-domain.com/upload";
        
        SimpleUploadUtil.uploadFile(filePath, serverUrl, new SimpleUploadUtil.UploadCallback() {
            @Override
            public void onSuccess(String result) {
                System.out.println("上传成功: " + result);
            }

            @Override
            public void onFailure(String errorMsg) {
                System.out.println("上传失败: " + errorMsg);
            }
        });
    }
}
