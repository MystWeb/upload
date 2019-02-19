package com.proaim.service.impl;

import com.proaim.entity.Picture;
import com.proaim.mapper.PictureMapper;
import com.proaim.service.PictureService;
import com.proaim.utils.PageResult;
import com.proaim.utils.PageUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.List;

/**
 * @date 2019/2/19
 */

@Service("pictureService")
public class PictureServiceImpl implements PictureService {
    @Autowired
    private PictureMapper pictureMapper;

    @Override
    public PageResult getPicturePage(PageUtil pageUtil) {
        List<Picture> pictures = pictureMapper.findPictures(pageUtil);
        int total = pictureMapper.getTotalPictures(pageUtil);
        PageResult pageResult = new PageResult(pictures, total, pageUtil.getLimit(), pageUtil.getPage());
        return pageResult;
    }

    @Override
    public Picture queryObject(Integer id) {
        return pictureMapper.findPictureById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int save(Picture picture) {
        try {
            return pictureMapper.insertPicture(picture);
        } catch (Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            e.printStackTrace();
            return 0;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(Picture picture) {
        try {
            return pictureMapper.updPicture(picture);
        } catch (Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            e.printStackTrace();
            return 0;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int delete(Integer id) {
        try {
            return pictureMapper.delPicture(id);
        } catch (Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            e.printStackTrace();
            return 0;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteBatch(Integer... ids) {
        try {
            return pictureMapper.deleteBatch(ids);
        } catch (Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            e.printStackTrace();
            return 0;
        }
    }
}
