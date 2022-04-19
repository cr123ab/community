package com.nowcoder.community.controller;

import com.nowcoder.community.annotation.LoginRequired;
import com.nowcoder.community.dao.UserMapper;
import com.nowcoder.community.entity.User;
import com.nowcoder.community.service.FollowService;
import com.nowcoder.community.service.LikeService;
import com.nowcoder.community.service.UserService;
import com.nowcoder.community.util.CommunityConstant;
import com.nowcoder.community.util.CommunityUtil;
import com.nowcoder.community.util.CookieUtil;
import com.nowcoder.community.util.HostHolder;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.FileHandler;

@Controller
@RequestMapping("/user")
public class UserController implements CommunityConstant {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Value("${community.path.upload}")
    private String uploadPath;

    @Value("${community.path.domain}")
    private String domain;

    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Autowired
    private UserService userService;

    @Autowired
    private HostHolder hostHolder;

    @Autowired
    private LikeService likeService;

    @Autowired
    private FollowService followService;

    @Autowired
    private UserMapper userMapper;

    @LoginRequired
    @RequestMapping(path = "/setting",method = RequestMethod.GET)
    public String getSettingPage(){
        return "/site/setting";
    }

    @LoginRequired
    @RequestMapping(path = "/upload",method = RequestMethod.POST)
    public String uploadHeader(MultipartFile headerImage, Model model){
        if (headerImage == null){
            model.addAttribute("error","你还没有选择图片");
            return "/site/setting";
        }

        String fileName = headerImage.getOriginalFilename();
        String suffix = fileName.substring(fileName.lastIndexOf("."));
        if (StringUtils.isBlank(suffix)){
            model.addAttribute("error","文件格式不正确");
            return "/site/setting";
        }

        //生成随机文件名
        fileName = CommunityUtil.generateUUID() + suffix;
        //确定文件存放路径
        File dest = new File(uploadPath + "/" + fileName);
        try {
            headerImage.transferTo(dest);
        } catch (IOException e) {
            logger.error("上传文件失败:" + e.getMessage());
            throw  new RuntimeException("上传文件失败，服务器发生异常");
        }

        //更显当前用户头像路径(web访问路经)
        //http://localhost:8080/community/user/header/xxx.png
        User user = hostHolder.getUser();
        String headerUrl = domain + contextPath + "/user/header/" + fileName;
        userService.updateHeader(user.getId(),headerUrl);

        return "redirect:/index";
    }

    @RequestMapping(path = "/header/{fileName}",method = RequestMethod.GET)
    public void getHeader(@PathVariable("fileName") String fileName, HttpServletResponse response){
        //服务器存放路径
        fileName = uploadPath + "/" + fileName;
        //文件后缀
        String suffix = fileName.substring(fileName.lastIndexOf("."));
        //响应图片
        response.setContentType("image/" + suffix);
        try (
            FileInputStream fis = new FileInputStream(fileName);
            OutputStream os = response.getOutputStream();
                ){
            byte[] buffer = new byte[1024];
            int b = 0;
            while ((b = fis.read(buffer)) != -1){
                os.write(buffer,0,b);
            }
        } catch (IOException e) {
            logger.error("读取头像失败：" + e.getMessage());
        }


    }

    @LoginRequired
    @RequestMapping(value = "/updatepw", method = RequestMethod.POST)
    public String updatePassword(String userName, String newUserName, String repeatUserName, Model model, HttpServletResponse response, HttpServletRequest request){
        if (userName == null){
            model.addAttribute("pwMsg1", "密码不能为空！");
            return "/site/setting";
        }
        if (newUserName == null){
            model.addAttribute("pwMsg2", "新密码不能为空！");
            return "/site/setting";
        }
        if (!newUserName.equals(repeatUserName)){
            model.addAttribute("pwMsg3", "两次密码不一致！");
            return "/site/setting";
        }

        // 检验原密码是否正确
        User user = hostHolder.getUser();
        // 为原密码加密
        userName = CommunityUtil.md5(userName + user.getSalt());
        if (!user.getPassword().equals(userName)){
            model.addAttribute("pwMsg1", "原密码输入错误！");
            return "/site/setting";
        }

        userService.updatePassword(user.getId(), newUserName);
        // 从cookie中获取凭证
        String ticket = CookieUtil.getValue(request, "ticket");
        userService.logout(ticket);
        return "redirect:/login";
    }



    //个人主页
    @RequestMapping(path = "/profile/{userId}", method = RequestMethod.GET)
    public String getProfilePage(@PathVariable("userId") int userId, Model model){
        User user = userService.findUserById(userId);
        if (user == null){
            throw  new RuntimeException("该用户不存在");
        }

        //用户
        model.addAttribute("user",user);
        //点赞数量
        int likeCount = likeService.findUserLikeCount(userId);
        model.addAttribute("likeCount",likeCount);

        //关注数量
        long followeeCount = followService.findFolloweeCount(userId, ENTITY_TYPE_USER);
        model.addAttribute("followeeCount",followeeCount);

        //粉丝数量
        long followerCount = followService.findFollowerCount(ENTITY_TYPE_USER,userId);
        model.addAttribute("followerCount",followerCount);
        //是否已关注
        boolean hasFollowed = false;
        if (hostHolder.getUser() != null){
            hasFollowed = followService.hasFollowed(hostHolder.getUser().getId(), ENTITY_TYPE_USER, userId);
        }
        model.addAttribute("hasFollowed",hasFollowed);

        return "/site/profile";
    }

}
