package com.proaim.controller;

import com.proaim.common.Result;
import com.proaim.common.ResultGenerator;
import com.proaim.utils.FileUtil;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;

/**
 * @date 2019/2/19
 */
@RestController
@RequestMapping("/upload")
public class UploadFileController {

    /**
     * @param chunks 当前所传文件的分片总数
     * @param chunk  当前所传文件的当前分片数
     * @Description: 大文件上传前分片检查
     */
    @RequestMapping("/checkChunk")
    public Result checkChunk(HttpServletRequest request, String guid, Integer chunks, Integer chunk, String fileName) {
        try {
            //上传存储路径
            String uploadDir = FileUtil.getRealPath(request);
            //后缀名
            String ext = fileName.substring(fileName.lastIndexOf("."));
            // 判断文件是否分块
            if (chunks != null && chunk != null) {
                //文件路径
                StringBuffer tempFileName = new StringBuffer();
                // file.separator这个代表系统目录中的间隔符，说白了就是斜线
                // 不过有时候需要双线，有时候是单线，你用这个静态变量就解决兼容问题了。(如果要考虑跨平台，则最好是这么写)
                // 等价于 uploadDir + "\\temp\\" + guid + "\\" + chunk + ext
                tempFileName.append(uploadDir).append(File.separator).append("temp").append(File.separator).append(guid).append(File.separator).append(chunk).append(ext);
                File tempFile = new File(tempFileName.toString());
                //是否已存在分片,如果已存在分片则返回SUCCESS结果
                if (tempFile.exists()) {
                    return ResultGenerator.genSuccessResult("分片已经存在！跳过此分片！");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResultGenerator.genFailResult("error");
        }
        return ResultGenerator.genNullResult("不存在分片");
    }

    /**
     * @param chunks 当前所传文件的分片总数
     * @param chunk  当前所传文件的当前分片数
     * @Description: 大文件分片上传
     */
    @RequestMapping("/files")
    public Result upload(HttpServletRequest request, String guid, Integer chunks, Integer chunk, String name, MultipartFile file) {
        String filePath = null;
        //上传存储路径
        String uploadDir = FileUtil.getRealPath(request);
        //后缀名
        String ext = name.substring(name.lastIndexOf("."));
        StringBuilder tempFileDir = new StringBuilder();
        StringBuilder tempFileName = new StringBuilder();
        // file.separator这个代表系统目录中的间隔符，说白了就是斜线
        // 不过有时候需要双线，有时候是单线，你用这个静态变量就解决兼容问题了。(如果要考虑跨平台，则最好是这么写)
        // 等价于 uploadDir + "\\temp\\" + guid + "\\" + chunk + ext
        tempFileDir.append(uploadDir).append(File.separator).append("temp").append(File.separator).append(guid).append(File.separator);
        tempFileName.append(uploadDir).append(File.separator).append("temp").append(File.separator).append(guid).append(File.separator).append(chunk).append(ext);
        File tempDir = new File(tempFileDir.toString());
        File tempName = new File(tempFileName.toString());
        // 判断文件是否分块
        if (chunks != null && chunk != null) {
            //根据guid 创建一个临时的文件夹
            if (!(tempDir.exists() && tempDir.isDirectory())) {
                tempDir.mkdirs();
            }
            try {
                //保存每一个分片
                file.transferTo(tempName);
            } catch (Exception e) {
                e.printStackTrace();
            }
            //如果当前是最后一个分片，则合并所有文件
            if (chunk == (chunks - 1)) {
                StringBuilder tempFileFolder = new StringBuilder();
                //等价于 uploadDir + "\\temp\\" + guid + File.separator
                tempFileFolder.append(uploadDir).append(File.separator).append("temp").append(File.separator).append(guid).append(File.separator);
                String newFileName = FileUtil.mergeFile(chunks, ext, tempFileFolder.toString(), request);
                filePath = "upload/chunked/" + newFileName;
            }
        } else {
            //不用分片的文件存储到files文件夹中
            StringBuilder destPath = new StringBuilder();
            destPath.append(uploadDir).append(File.separator).append("files").append(File.separator);
            String newName = System.currentTimeMillis() + ext;// 文件新名称
            try {
                FileUtil.saveFile(destPath.toString(), newName, file);
            } catch (IOException e) {
                e.printStackTrace();
            }
            filePath = "upload/files/" + newName;
        }
        Result result = ResultGenerator.genSuccessResult();
        result.setData(filePath);
        return result;
    }
}
