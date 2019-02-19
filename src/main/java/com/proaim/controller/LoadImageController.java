package com.proaim.controller;

import com.proaim.common.Result;
import com.proaim.common.ResultGenerator;
import org.apache.commons.io.FileUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

/**
 * @date 2019/2/19
 */
@RestController
@RequestMapping("/images")
public class LoadImageController {

    @PostMapping("")
    public Result upload(HttpServletRequest request, @RequestParam("file") MultipartFile file) throws IOException {
        ServletContext sc = request.getSession().getServletContext();
        //获取文件的绝对路径，并且绝对路径都是以 / 开头
        String dir = sc.getRealPath("/upload");
        //获取文件类型
        String type = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf(".") + 1, file.getOriginalFilename().length());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        Random random = new Random();
        String imgName = "";
        if ("jpg".equals(type)) {
            imgName = sdf.format(new Date()) + random.nextInt(100) + ".jpg";
        } else if ("png".equals(type)) {
            imgName = sdf.format(new Date()) + random.nextInt(100) + ".png";
        } else if ("jpeg".equals(type)) {
            imgName = sdf.format(new Date()) + random.nextInt(100) + ".jpeg";
        } else if ("gif".equals(type)) {
            imgName = sdf.format(new Date()) + random.nextInt(100) + ".gif";
        } else {
            return null;
        }
        //将文件流写入到磁盘中
        FileUtils.writeByteArrayToFile(new File(dir, imgName), file.getBytes());
        Result result = ResultGenerator.genSuccessResult();
        result.setData("/upload/" + imgName);
        //返回文件路径
        return result;
    }
}
