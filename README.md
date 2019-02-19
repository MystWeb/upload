#### 全文简介

上传图片是比较常见和被用户熟知的功能模块，常用场景有头像设置、产品预览图、新闻头图等等，在这些场景中都需要使用到图片上传功能，本篇文章将会对文件上传的大致流程及功能设计进行详细的介绍，并通过 SpringMVC 实现图片管理模块的相关功能，在实现单图上传功能后，会对文件上传进行扩展，企业级应用一般都会包含多图上传及大文件分片上传功能。一些小型网站，当前可能并没有这些功能，但随着日后的成长，系统的不断升级，总归会有这些方面的功能需求。

本文将从图片上传模块及SpringMVC处理文件上传的流程开始讲起，逐步分析多图上传、大文件上传、断点续传的处理流程。结合着功能的实现、Demo 的演示及详细的流程分析，让大家知其然知其所以然。

### SpringMVC 实现文件上传

先通过源码分析，具体介绍下 SpringMVC 是如何进行文件上传处理的。其中包括代码调用时序图的绘制，以及对 SpringMVC 框架中文件处理部分代码的解析。

#### 时序图

利用 SpringMVC 实现文件上传功能，离不开对 `MultipartResolver` 的设置，`MultipartResolver`这个类，你可以将其视为 SpringMVC 实现文件上传功能时的工具类，这个类也只会在文件上传中发挥作用，在配置了具体实现类之后，SpringMVC 中的 `DispatcherServlet` 在处理请求时会调用 `MultipartResolver` 中的方法判断此请求是不是文件上传请求，如果是的话 `DispatcherServlet` 将调用 `MultipartResolver` 的 `resolveMultipart(request)` 方法对该请求对象进行装饰并返回一个新的 `MultipartHttpServletRequest` 供后继处理流程使用，注意!此时的请求对象会由 `HttpServletRequest` 类型转换成 `MultipartHttpServletRequest` 类型，这个类中会包含所上传的文件对象可供后续流程直接使用而无需自行在代码中实现对文件内容的读取逻辑。

根据这一过程，绘制了如下代码调用时序图：

![enter image description here](https://images.gitbook.cn/296f83a0-ab44-11e8-b6c8-d74329e78a24)

如上图所示，当收到请求时，DispatcherServlet 的 `checkMultipart()` 方法会调用 MultipartResolver 的 `isMultipart()` 方法判断请求中是否包含文件。

如果请求数据中包含文件，则调用 MultipartResolver 的 `resolveMultipart()` 方法对请求的数据进行解析，然后将文件数据解析成 MultipartFile 并封装在 MultipartHttpServletRequest（继承了 HttpServletRequest）对象中，最后传递给 Controller 控制器，

#### 源码分析

从上面的时序图中，可以看出我们选用的 MultipartResolver 是 `CommonsMultipartResolver`实现类：

```
//CommonsMultipartResolver实现了MultipartResolver接口，是它的一个具体实现类

public class CommonsMultipartResolver extends CommonsFileUploadSupport implements MultipartResolver, ServletContextAware{

}
```

接下来，我们更进一步，深入到源码中，具体分析时序图所展示的、实现文件上传的代码调用过程。

**首先，我们看下 DispatcherServlet 收到 Request 请求后的执行步骤。**

- 首先分析判断 HttpServletRequest 请求，判断此对象中是否包含文件信息。
- 如果包含文件，则调用相应的方法将文件对象封装到 Request 中，源码如下：

```
    protected HttpServletRequest checkMultipart(HttpServletRequest request) throws MultipartException {
        //1.判断是否包含文件
        if (this.multipartResolver != null && this.multipartResolver.isMultipart(request)) {
            if (WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class) != null) {
                this.logger.debug("Request is already a MultipartHttpServletRequest - if not in a forward, this typically results from an additional MultipartFilter in web.xml");
            } else {
                if (!(request.getAttribute("javax.servlet.error.exception") instanceof MultipartException)) {
                    //2.将文件对象封装到Request中
                    return this.multipartResolver.resolveMultipart(request);
                }
                this.logger.debug("Multipart resolution failed for current request before - skipping re-resolution for undisturbed error rendering");
            }
        }

        return request;
    }
```

其中 `this.multipartResolver.isMultipart(request)` 则是调用 CommonsMultipartResolver 的 `isMultipart()` 方法，源码如下：

```
public boolean isMultipart(HttpServletRequest request) {
        return request != null && ServletFileUpload.isMultipartContent(request);
    }
```

跟踪源码调用链得出最终调用的方法是 FileUploadBase.java：

```
public static final boolean isMultipartContent(RequestContext ctx) {
        String contentType = ctx.getContentType();
        if (contentType == null) {
            return false;
        } else {
            return contentType.toLowerCase(Locale.ENGLISH).startsWith("multipart/");
        }
    }
```

一路分析下来，我们清晰地得出了具体的判断逻辑。首先判断请求对象 request，之后对请求头中的 contentType 对象进行判断。请求对象不为空且 contentType 不为空且 contentType 的值以 `multipart/` 开头，此时会返回 true，否则将不会将这次请求标示为文件上传请求。

返回 true 后，表明此次请求中含有文件，接下来 DispatcherServlet 将会调用 `resolveMultipart(request)` 重新封装 Request 对象，实际调用的是 CommonsMultipartResolver 的 `resolveMultipart()` 方法，源码如下：

```
    public MultipartHttpServletRequest resolveMultipart(final HttpServletRequest request) throws MultipartException {
        Assert.notNull(request, "Request must not be null");
        if (this.resolveLazily) {
            return new DefaultMultipartHttpServletRequest(request) {
                protected void initializeMultipart() {
                    MultipartParsingResult parsingResult = CommonsMultipartResolver.this.parseRequest(request);
                    this.setMultipartFiles(parsingResult.getMultipartFiles());
                    this.setMultipartParameters(parsingResult.getMultipartParameters());
                    this.setMultipartParameterContentTypes(parsingResult.getMultipartParameterContentTypes());
                }
            };
        } else {
            MultipartParsingResult parsingResult = this.parseRequest(request);
            return new DefaultMultipartHttpServletRequest(request, parsingResult.getMultipartFiles(), parsingResult.getMultipartParameters(), parsingResult.getMultipartParameterContentTypes());
        }
    }
```

跟踪源码调用链，得出最终调用的方法是：

```
    protected MultipartParsingResult parseRequest(HttpServletRequest request) throws MultipartException {
        String encoding = this.determineEncoding(request);
        FileUpload fileUpload = this.prepareFileUpload(encoding);

        try {
            //1.获取所有文件对象并封装为列表
            List<FileItem> fileItems = ((ServletFileUpload)fileUpload).parseRequest(request);
            //2.返回封装后的Request对象
            return this.parseFileItems(fileItems, encoding);
        } catch (SizeLimitExceededException var5) {
            throw new MaxUploadSizeExceededException(fileUpload.getSizeMax(), var5);
        } catch (FileUploadException var6) {
            throw new MultipartException("Could not parse multipart servlet request", var6);
        }
    }
```

由上面代码可以看出，分别调用的是 FileUploadBase.java 的 `parseRequest()` 方法和 CommonsMultipartResolver 的 `parseFileItems()` 方法。

由于篇幅限制就不再继续贴代码了，感兴趣的朋友可以自行查看，牵涉到的类名和方法都已列举出来。

之后就可以在具体的 Controller 类中直接使用文件对象，而不用自行实现文件对象的解析了。

#### 图片上传实现

通过源码的解析及整个方法调用过程，大致的梳理了上传图片时 SpringMVC 的具体处理逻辑，在之后的开发过程中也能够更放心地使用它们，之后实现图片上传功能，直接使用此方式即可。

接下来，我们将具体的编写代码来使用 SpringMVC 实现图片上传功能。

##### **pom.xml**

实现文件上传时需要依赖相关 Jar 包，我们首先在 pom 文件中将依赖包添加进来：

```
 <!-- Start: commons相关依赖包 -->
        <dependency>
            <groupId>commons-io</groupId>
            <artifactId>commons-io</artifactId>
            <version>${commons-io.version}</version>
        </dependency>
        <dependency>
            <groupId>commons-fileupload</groupId>
            <artifactId>commons-fileupload</artifactId>
            <version>${commons-fileupload.version}</version>
        </dependency>
<!-- Start: commons相关依赖包 -->
```

##### **spring-mvc.xml**

如下设置 MultipartResolver，我们使用的是仍是 CommonsMultipartResolver 实现类：

```
    <bean id="multipartResolver"  class="org.springframework.web.multipart.commons.CommonsMultipartResolver">
    <!-- 设定默认编码 -->
    <property name="defaultEncoding" value="UTF-8"></property>
    <!-- 设定文件上传的最大值为5MB，5*1024*1024 -->
    <property name="maxUploadSize" value="5242880"></property>
    </bean>
```

##### **LoadImageController.java**

通过前文中的源码分析，可知文件对象已被封装到 MultipartFile 对象中，在代码中可以直接使用此文件对象，之后调用 File 相关方法将文件存储到 upload 目录下，代码如下：

```
public Result upload(HttpServletRequest request, @RequestParam("file") MultipartFile file) throws IOException {
        ServletContext sc = request.getSession().getServletContext();
        String dir = sc.getRealPath("/upload");
        String type = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf(".") + 1, file.getOriginalFilename().length());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        Random r = new Random();
        String imgName = "";
        if ("jpg".equals(type)) {
            imgName = sdf.format(new Date()) + r.nextInt(100) + ".jpg";
        } else if ("png".equals(type)) {
            imgName = sdf.format(new Date()) + r.nextInt(100) + ".png";
        } else if ("jpeg".equals(type)) {
            imgName = sdf.format(new Date()) + r.nextInt(100) + ".jpeg";
        } else if ("gif".equals(type)) {
            imgName = sdf.format(new Date()) + r.nextInt(100) + ".gif";
        } else {
            return null;
        }
        //将文件流写入到磁盘中
        FileUtils.writeByteArrayToFile(new File(dir, imgName), file.getBytes());
        //返回文件路径
        return Result.ok().put("url", "/upload/" + imgName);
    }
```

至此，通过 SpringMVC 处理文件上传的过程已经介绍完毕。

### 实现图片管理模块

基于以上基础知识，接下来，我们就开始介绍图片管理模块的具体实现过程。

#### 页面与交互

图片模块页面构成主要为主页面和信息编辑弹框。

##### **功能划分**

主页面的设计效果图，如下所示：

![enter image description here](https://images.gitbook.cn/93cb9a40-ab44-11e8-b6c8-d74329e78a24)

如上图所示，图片模块页面的布局组成为：

- **模块标题区域**
- **模块功能区域**

其中，模块功能区又包含功能按钮区域、列表信息区域和分页信息区域。

信息编辑弹框设计效果图，如下所示：

![enter image description here](https://images.gitbook.cn/9c408500-ab44-11e8-b6c8-d74329e78a24)

由上图可知，信息编辑弹框区域的组成为：

- 标题区域
- 错误提示区域
- 图片预览区域
- 上传按钮
- 信息输入区
- 表单提交区域

##### **操作**

主页面包括如下操作：

- 按钮点击
- 记录选择
- 翻页

添加/修改按钮点击后会出现信息编辑弹框，此时又会产生如下操作：

- 文件上传
- 信息输入
- 请求提交

##### **反馈效果**

接下来，我们看下图片模块包含哪些交互，交互过程是怎样的。

- 列表数据重新加载：页面初始化时或者点击分页按钮时，JqGrid 会对列表数据进行渲染及重新渲染。
- 弹框：点击 “ 添加 ” 或者 “ 修改 ” 按钮后会显示信息编辑弹框。
- 选中提示：点击 “ 编辑 ” 按钮前，如果未选中一条编辑记录或者选中了多条编辑记录，都会弹出此提示。点击删除按钮前，如果未选中记录也会出现此提示。
- 错误提示区显示：用户信息输入不规范会看到此错误提示。
- 请求处理完成提示：添加请求、修改请求、删除请求完成后会出现此提示。

#### 前端实现

前端页面代码文件，我们命名为 picture.html，实现交互和逻辑代码的 JS 文件为 dist 目录下的 picture.js 文件。

##### **初始化 uploader**

前端的文件上传插件，我们使用的是 JQuery 的 ajaxupload 工具。接下来，带大家了解如何在前端页面中使用它。

首先，在页面中引入依赖文件：

```
<!-- ajax upload -->
<script src="plugins/ajaxupload/ajaxupload.js"></script>
```

然后，设置上传按钮 DOM 对象：

```
    <div class="col-sm-10">
           <a class="btn btn-info" id="upload"><i class="fa fa-picture-o"></i> 上传文件</a>
     </div>
```

上传代码逻辑如下，首先判断文件格式，图片上传限制文件格式为 jpg、png、gif，其他格式的文件将不会被处理，之后向后端发送文件上传请求，并根据后端返回数据进行相应的事件处理。

```
    new AjaxUpload('#upload', {
        action: 'images',
        name: 'file',
        autoSubmit: true,
        responseType: "json",
        onSubmit: function (file, extension) {
            if (!(extension && /^(jpg|jpeg|png|gif)$/.test(extension.toLowerCase()))) {
                alert('只支持jpg、png、gif格式的图片！', {
                    icon: "error",
                });
                return false;
            }
        },
        onComplete: function (file, r) {
            if (r.resultCode == 200) {
                alert("上传成功");
                $("#picturePath").val(r.data);
                $("#img").attr("src", r.data);
                $("#img").attr("style", "width: 100px;height: 100px;display:block;");
                return false;
            } else {
                alert(r.message);
            }
        }
    });
```

以下通过注释对 ajaxupload 插件初始化时的主要参数均做了说明：

```
    new AjaxUpload('#upload', {//上传按钮DOM
        //文件上传后台处理url
        action: 'images',
        //参数名称，对应的是Controller中的 @RequestParam("file") MultipartFile file，如果两个名称不同则会报错
        name: 'file',
        //是否自动提交
        autoSubmit: true,
        //服务器返回的数据类型
        responseType: "json",
        //请求提交前执行的函数
        onSubmit: function (file, extension) {
        },
        //请求完成后的回调函数
        onComplete: function (file, r) {
        }
    });
```

##### **功能代码**

本部分主要对图片信息添加功能的实现进行讲解，其他功能可结合项目源码和流程图自行学习理解。

在信息编辑弹框页面中，当文件上传完成、备注信息输入完成后点击 “ 确认 ” 按钮，首先会执行 `validObject()` 方法校验输入参数，校验逻辑通过后则进行数据封装，并发送网络请求至后端。之后根据后端返回的 result 对象进行对应的操作，如果出现报错则直接提醒用户错误信息，如果后端返回成功则根据不同的 resultCode 进行对应的操作。resultCode 等于 200，则表示请求成功，关闭弹框、提示用户保存成功并重新加载图片信息列表数据，代码实现如下图所示：

```
$('#saveButton').click(function () {
    //验证数据
    if (validObject()) {
        //一切正常后发送网络请求
        //ajax
        var id = $("#pictureId").val();
        var picturePath = $("#picturePath").val();
        var pictureRemark = $("#pictureRemark").val();
        var data = {"path": picturePath, "remark": pictureRemark};
        $.ajax({
            type: 'POST',//方法类型
            dataType: "json",//预期服务器返回的数据类型
            url: pictures/save,//url
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify(data),
            success: function (result) {
                if (result.resultCode == 200) {
                    $('#pictureModal').modal('hide');
                    alert("保存成功");
                    reload();
                } else {
                    $('#pictureModal').modal('hide');
                    alert("保存失败");
                };
            }
        });

    }
});
```

功能演示如下：

![enter image description here](https://images.gitbook.cn/a8b9b9f0-ab44-11e8-b6c8-d74329e78a24)

#### 后端实现

##### **表结构设计**

新增 `tb_picture` 表用来存储图片信息，建表语句如下：

```
use gitchat_ssm_demo_db;
DROP TABLE IF EXISTS `tb_picture`;
CREATE TABLE `tb_ssm_picture` (
  `id` bigint(11) unsigned NOT NULL AUTO_INCREMENT COMMENT '自增id',
  `path` varchar(50) NOT NULL DEFAULT '' COMMENT '图片路径',
  `remark` varchar(200) NOT NULL DEFAULT '' COMMENT '备注',
  `is_deleted` tinyint(4) NOT NULL DEFAULT 0 COMMENT '是否已删除 0未删除 1已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
```

##### 代码实现

图片管理模块的代码实现可自行查看业务代码，源码实现已经上传至百度云盘，在文章末给出，这里就不再占用篇幅了。

### WebUploader

进行实际编码前，先对使用到的技术做下简单介绍。多图上传及文件分片上传 Demo 实现所使用的前端插件是 **WebUploader**，一个优秀的文件处理插件。

> WebUploader 是由 Baidu WebFE（FEX） 团队开发的一个简单的以 HTML 5 为主，Flash 为辅的现代文件上传组件。在现代浏览器里面能充分发挥 HTML 5 的优势，同时又不摒弃主流 IE 浏览器，沿用原来的 Flash 运行时，兼容 IE6+、iOS 6+、Android 4+。两套运行时，同样的调用方式，可供用户任意选用。

想进一步了解，可访问：

- [WebUploader官方网站](http://fex.baidu.com/webuploader/)
- [WebUploader in GitHub](https://github.com/fex-team/webuploader)

![enter image description here](https://images.gitbook.cn/afec60b0-ab44-11e8-b6c8-d74329e78a24)

除上图中列举的特性，也根据自己的经验简单总结了它的优点，即兼容性好、学习成本低。

很多 Web 开源作品也在使用此插件，受欢迎程度还是很高的，网上关于 WebUploader 的教程有很多，官方也提供了学习文档和 Demo，学习起来并不复杂，只需引入 WebUploader 必须的依赖文件，并设置好 DOM 属性，即可初始化 WebUploader 对象，实现文件的上传。

### 多图上传

#### 什么是多图上传？

前文 “ 图片管理模块 ” 小节中，我们已实现了图片上传功能，效果如下：

![enter image description here](https://images.gitbook.cn/b7b13550-ab44-11e8-b6c8-d74329e78a24)

该 Demo 中实现了图片的上传及回显功能，这种形式只是单图上传，即点击上传插件按钮后只能选择一张图片，一次也只能上传一张图片至服务端，同时前端页面也只需要处理这一张图片即可。了解了这个逻辑，多图上传的意思也就十分清晰了，即点击上传按钮后可以在文件框中选择多张图片并完成上传。

#### 为什么要使用多图上传？

单图上传的流程大家已经清楚，多数情况下该功能也没有什么要特别说明的，但遇到某些特殊情况后，痛点就来了，比如租房网络平台中上传租房详情信息的功能实现。

上传租房信息，房间预览图是不可缺少的，而且越多越好、越详细越好。这时，我们仍使用单图上传，上传图片的总数量少，用户勉强还能应付。如果上传上百甚至上千张图片，再使用单片上传，用户怕是就难以承受了。

由上看出，此场景下，单图上传就无法满足业务需求了。所以面对需要上传大量图片时，我们就要考虑使用多图上传功能了。尤其对于维护各种数据的后台管理系统来说，这个功能更是不可缺少。

#### 多图上传是什么样的效果？

我简单实现了一个多图上传的 Demo，效果如下：

![enter image description here](https://images.gitbook.cn/c34cb470-ab44-11e8-b6c8-d74329e78a24)

点击 “ 选择图片 ” 按钮，弹出文件选择框，框选多张图片，选择完成后点击上传，多张图片就被上传到服务器上，图片路径也全部被返回至前端。

相对之下，在租房详情页上传预览图片，通过这种方式一次选择 20 张图片比与点击 20 次上传按钮，是不是更有优势呢。

### 多图上传功能实现与原理分析

接下来，我们将通过具体代码实例，带大家了解多图上传功能的实现过程，同时，还将对多图上传的原理和涉及到的功能点进行分析，让大家对多图上传有一个更加清晰的认识。

#### 多图上传功能实现

下面我就带大家实现一个简单的多图上传 Demo，步骤如下。

**1.** 新建 `webuploader-test.html` 文件。

我们在该页面中实现基本的页面样式，并添加多图上传所需的 DOM 设置，代码如下:

```
<div id="uploader-demo">
   <div>
   <div id="fileUploader">选择图片</div>
   <button id="upload" class="btn btn-default">开始上传</buttton>
   </div>
</div>
```

**2.** 下载 WebUploader 资源并引入依赖文件。

首先下载 WebUploader 资源包，解压后复制到 `webapp/plugins` 目录下，之后在 HTML 文件中引入依赖文件。

引入 JS 文件的代码如下：

```
<script src="plugins/webuploader/webuploader.min.js"></script>
```

引入 CSS 样式文件，代码如下：

```
<link href="plugins/webuploader/webuploader.css" rel="stylesheet"/>
```

**3.** 实现上传文件代码。

初始化 WebUploader，初始化参数及参数释义如下：

```
        var thumbnailWidth = 0.5;   //缩略图高度和宽度，当宽高度是0~1的时候，按照百分比计算
        var thumbnailHeight = 0.5;
        var uploader = WebUploader.create({
            auto: false,// 选完文件后，是否自动上传
            swf: 'plugins/webupload/Uploader.swf',// swf文件路径
            server: '/images',// 文件接收服务端url
            method: 'POST',// 服务端请求方法
            pick: '#fileUploader',// 选择文件的按钮
            fileNumLimit: 10,//文件总数量只能选择10个,多于10个部分不会加入上传队列
            fileSizeLimit: 100 * 1024 * 1024,//验证文件总大小是否超出限制, 超出则不允许加入队列 100M
            fileSingleSizeLimit: 4 * 1024 * 1024,//验证单个文件大小是否超出限制, 超出则不允许加入队列 4M
            compress: false,//配置压缩的图片的选项。如果此选项为false, 则图片在上传前不进行压缩。
            threads: 4,//上传并发数,允许同时最大上传进程数,默认值为3
            accept: {//指定接受哪些类型的文件
                title: 'Images',
                extensions: 'gif,jpg,jpeg,bmp,png',// 只允许选择部分图片格式文件，可自行修改
                mimeTypes: 'image/*'
            },
        });
```

上传事件注册，点击 “ 上传图片 ” 按钮后会执行如下代码：

```
$("#upload").click(function () {
                $("#imgResult").html('');
                //文件处理时增加了alert事件,不需要的话自行删除即可
                alert("开始上传");
                uploader.upload();
                alert("上传完成");
            }
        )
```

**4.** 实现后端功能。

后端功能实现依然使用 `LoadImageController.java` 类，和前文中的单图上传处理类一致，用来接收文件对象并实现文件存储和路径返回，这里就不贴代码了。

这里给大家留一个问题：**为什么处理单张图片的 Controller 可以用来做多文件的请求处理类呢？**

#### 多图上传功能演示

项目部署成功后，打开 `localhost:8080/webuploader-test.html` 多图上传页面。点击 “ 选择图片 ” 按钮，选择多张图片点击 “ 打开 ” 返回到多图上传页面，同时页面上出现了所选图片的预览图。注意！此时还没有进行图片上传请求，这些预览图由 WebUploader 插件生成，确认无误后点击 “ 开始上传 ” 按钮，开始发送请求。

上传成功后，页面中图片存储路径一栏中出现了所有图片的访问路径，同时预览图下方的状态会修改为 “ 上传成功 ”。我们可以选择其中某个图片路径访问看它是否能正常显示，整个过程如下：

![enter image description here](https://images.gitbook.cn/d15ae140-ab44-11e8-b6c8-d74329e78a24)

之后，还可以再去验证下 Tomcat 服务器中的 upload 目录，所有上传的文件都会存储在该目录中，过程如下：

![enter image description here](https://images.gitbook.cn/d8ad9320-ab44-11e8-b6c8-d74329e78a24)

演示和验证完毕，多图上传功能一切正常。

#### 多图上传功能分析

> 为什么处理单张图片的 Controller 可以用来做多文件的请求处理类呢？

不知道各位小伙伴有答案了没有，这一节将会对这个问题进行解答。

![enter image description here](https://images.gitbook.cn/e223e850-ab44-11e8-b6c8-d74329e78a24)

结合动图，我们先来看看整个上传过程。如上图所示，在上传图片前先打开浏览器的控制台面板，点击 “ Network ” 来监控整个请求发起过程。上传图片发送的是 AJAX 请求，点击 “ XHR ” 将其他类型的请求过滤掉；之后选择需要上传的多张图片，点击 “ 开始上传 ” 按钮进行图片上传，接着就可以看到所有的图片都上传完成。

了解了基本过程还不够，要解答上面问题，还需要研究 Network 下的所有请求。这里选择4张图片进行上传，右侧的 “ Network ” 面板中相应的出现了 4 个请求。这里有人可能会疑问，不应该是发送1次请求，将多张图片上传到服务器上吗？我们接着看右侧的请求分析，4 张图片的多图上传就是单图上传的流程乘以 4，后端处理流程并没有任何的修改和改变。

到这里，大家对小节开头的问题明白了吧。其实在程序实现中多图上传与单图上传本质上是一样的，只是前端实现上传的 JS 代码有差异罢了，将人为一张张上传图片这个重复步骤交给了前端代码来处理了。

朋友们可以自行下载本节的源代码进行研究，希望大家能够对多图上传的含义、流程、实现有一个更清晰透彻的理解。

### 大文件分片上传

文件上传是 Web 系统中的常见功能，比如上传图片、文件、视频等等。前面我们实现了图片的上传，其实其他文件的上传也比较简单，只需修改代码中关于文件格式的设置即可上传对应的文件。

相比之下，大文件的上传要更复杂一些，难点也较多，这一节我们将对这个功能进行分析和实现。

#### 难点

首先我们了解下大文件上传有哪些痛点，之后好对阵下药。

将痛点整理如下：

- 前端上传插件对文件大小进行限制；
- 服务器对请求大小进行限制。

这两个问题可以通过修改设置解决掉，不过依然会有接下来的问题：

- 传输文件过大导致带宽资源紧张，请求速度降低；
- 文件过大导致后端程序卡死，请求无响应；
- 由于请求无法及时响应，导致前端页面卡顿，用户体验下降；
- 甚至导致已经成功上传但是请求响应错误以致于用户进行重复上传的问题；
- 服务器资源紧张，使服务器压力增大。

#### 痛点分析

在分析以上问题产生的原因之前，我们先了解下上传文件的流程。后端程序将文件上传至服务器，首先会将其缓存为临时文件，或者缓存到内存中，之后再调用相关 API 移动临时文件或保存文件数据。处理较小的文件，这两个步骤不会出现太大问题，但处理较大文件时就会出现瓶颈，从而导致无法对大文件的上传及时做出响应。

其背后的原因主要有这几点。首先是服务器的内存不可能无限大，内存越大所花费的人民币成本也就越高。再者服务器内存是有限制的，文件越大导致内存紧张的几率就越大，而内存紧张进而会导致服务器的性能急速下降。其次，移动大文件与超大文件也是件比较耗时间与系统资源的事。

综上分析，我们知道大文件的处理会影响服务器性能，进而导致整个请求响应变慢甚至无响应，从而导致浏览器等待超时或者报错，甚至页面卡死，直至整个环节发生雪崩。所以，大文件直接上传是不可取的，即便功能实现了也会影响整个系统的使用。

### 大文件分片上传功能实现

你现在可能会在心里嘀咕，那怎么办呢？我们就需要这个功能，难道不做了吗？

既然直接上传的方式不可取，那我们就换一种实现方式。正如多图上传是单图上传的升级版，我们把小文件上传做下升级，不就可以上传大文件了。

我们可以把大文件切割成若干个小文件，全部传输到服务器后再进行合并，这样就可以实现大文件的上传了，不会再出现请求无响应，页面卡死的情况了。接下来将通过大文件上传的案例，讲述什么是分片上传，怎么实现分片上传。

#### 分片上传功能前端实现

初始化 WebUploader，与前文中提到的初始化方式略有不同，主要是增加了分片上传的参数：chunked、chunkSize。

```
var uploader = WebUploader.create({
            auto: false,// 选完文件后，是否自动上传
            swf: 'plugins/webupload/Uploader.swf',// swf文件路径
            server: '/upload/files',// 文件接收服务端url
            method: 'POST',// 服务端请求方法
            pick: '#picker',// 选择文件的按钮
            fileNumLimit: 10,//文件总数量只能选择10个,多于10个部分不会
            compress: false,//配置压缩的图片的选项。如果此选项为false, 则图片在上传前不进行压缩。
            chunked: true, //开启分块上传
            chunkSize: 5 * 1024 * 1024,//分片大小 默认5M
            chunkRetry: 3,//网络问题上传失败后重试次数
            threads: 1, //上传并发数 大文件时建议设置为1
            fileSizeLimit: 2000 * 1024 * 1024,//验证文件总大小是否超出限制, 超出则不允许加入队列 最大2000M
            fileSingleSizeLimit: 2000 * 1024 * 1024//验证单个文件大小是否超出限制, 超出则不允许加入队列  最大2000M
            //为了大文件处理就没有设置文件类型限制,可根据业务需求进行设置
            // accept: {//指定接受哪些类型的文件
            //     title: 'Images',
            //     extensions: 'gif,jpg,jpeg,bmp,png',// 只允许选择部分图片格式文件。
            //     mimeTypes: 'image/*'
            // },
        });
```

与之前多文件上传的初始化方式相比，增加了 chunked 参数开启分片，并通过 chunkSize 设置分片大小，同时 fileSizeLimit 和 fileSingleSizeLimit 两个文件大小限制的参数也相应做了调整。

注册 uploadBeforeSend 事件，在文件上传前进行切片和参数填充：

```
        //发送前填充数据
        uploader.on('uploadBeforeSend', function (block, data) {
            // block为分块数据。
            // file为分块对应的file对象。
            var file = block.file;
            // 修改data可以控制发送哪些携带数据。
            data.guid = guid;//guid
            data.fileName_ = $("#s_" + file.id).val();
            // 删除其他数据
            if (block.chunks > 1) { //文件大于chunksize 分片上传
                data.isChunked = true;
            } else {
                data.isChunked = false;
            }
        });
```

下面是上传事件：

```
$("#startUpload").click(function () {
            uploader.upload();//上传
        });
```

#### 分片上传功能后端实现

增加了分片验证逻辑，上传和合并过程与大文件上传处理流程相同，功能代码如下：

```
    /**
     * @param chunks 当前所传文件的分片总数
     * @param chunk  当前所传文件的当前分片数
     * @return
     * @Description: 大文件上传前分片检查
     * @author: 13
     */
    @ResponseBody
    @RequestMapping(value = "/checkChunk")
    public Result checkChunk(HttpServletRequest request, String guid, Integer chunks, Integer chunk, String fileName) {
        try {
            String uploadDir = FileUtil.getRealPath(request);
            String ext = fileName.substring(fileName.lastIndexOf("."));
            // 判断文件是否分块
            if (chunks != null && chunk != null) {
                //文件路径
                StringBuilder tempFileName = new StringBuilder();
                tempFileName.append(uploadDir).append(File.separator).append("temp").append(File.separator).append(guid).append(File.separator).append(chunk).append(ext);
                File tempFile = new File(tempFileName.toString());
                //是否已存在分片,如果已存在分片则返回SUCCESS结果
                    if (tempFile.exists()) {
                     return ResultGenerator.genSuccessResult("分片已经存在！跳过此分片！");
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return ResultGenerator.genFailResult("error");
        }
        return ResultGenerator.genNullResult("不存在分片");
    }
```

分片上传功能的后端实现与单图上传的实现逻辑有一些差别：

- 请求参数中增加了分片相关的参数；
- 文件存储逻辑变化，对应的设置分片存储的文件目录；
- 增加分片逻辑判断，如果是最后一个分片则进行文件合并；
- 增加分片文件合并操作，合并成功后删除所有分片文件。

```
    /**
     * @param chunks 当前所传文件的分片总数
     * @param chunk  当前所传文件的当前分片数
     * @return
     * @Description: 大文件分片上传
     * @author: 13
     */
    @ResponseBody
    @RequestMapping(value = "/files")
    public Result upload(HttpServletRequest request, String guid, Integer chunks, Integer chunk, String name,  MultipartFile file) {
        String filePath = null;
        //上传存储路径
        String uploadDir = FileUtil.getRealPath(request);
        //后缀名
        String ext = name.substring(name.lastIndexOf("."));
        StringBuilder tempFileName = new StringBuilder();
tempFileName.append(uploadDir).append(File.separator).append("temp").append(File.separator).append(guid).append(File.separator).append(chunk).append(ext);
        File tempFile = new File(tempFileName.toString());
        // 判断文件是否分块
        if (chunks != null && chunk != null) {
            //根据guid 创建一个临时的文件夹
            if (!tempFile.exists()) {
                tempFile.mkdirs();
            }
            try {
                //保存每一个分片
                file.transferTo(tempFile);
            } catch (Exception e) {
                e.printStackTrace();
            }
            //如果当前是最后一个分片，则合并所有文件
            if (chunk == (chunks - 1)) {
                StringBuilder tempFileFolder = new StringBuilder();
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
```

分片合并操作，如下：

```
public static String mergeFile(int chunksNumber, String ext, String uploadFolderPath,HttpServletRequest request) {
        String mergePath = uploadFolderPath;
        String destPath = getRealPath(request);// 文件路径
        String newName = System.currentTimeMillis() + ext;// 文件新名称
        SequenceInputStream s;
        InputStream s1;
        try {
            s1 = new FileInputStream(mergePath + 0 + ext);
            String tempFilePath;
            InputStream s2 = new FileInputStream(mergePath + 1 + ext);
            s = new SequenceInputStream(s1, s2);
            for (int i = 2; i < chunksNumber; i++) {
                tempFilePath = mergePath + i + ext;
                InputStream s3 = new FileInputStream(tempFilePath);
                s = new SequenceInputStream(s, s3);
            }
            //分片文件存储到/upload/chunked目录下
            StringBuilder filePath = new StringBuilder();
            filePath.append(destPath).append(File.separator).append("chunked").append(File.separator);
            saveStreamToFile(s, filePath.toString(), newName);
            // 删除保存分块文件的文件夹
            deleteFolder(mergePath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return newName;
    }
```

#### 分片上传功能演示

项目部署成功后，打开：`localhost:8080/webuploader-test2.html` 大文件上传页面，点击 “ 选择文件 ” 按钮，在出现的文件选择框中，点击选中一个 55M 的视频文件，之后点击 “ 开始上传 ” 按钮。

该上传过程与之前的单文件上传和多图上传有些不同，大文件上传的请求不会很快收到服务器的响应，而要稍等一段时间。过程中页面没有卡顿，唯一的变化是进度条，当进度条处于完成状态时，便立刻收到后端的请求响应，上传后的文件路径便出现在了页面上。

之后复制返回的文件路径，在浏览器中的找开，看看视频文件能否打开，以及能否正常播放，即可验证大文件上传功能是否正常。整个过程如下图所示：

![enter image description here](https://images.gitbook.cn/f72234a0-ab44-11e8-b6c8-d74329e78a24)

因为录屏软件对录制时间有限制，所以我没有选择特别大的文件进行演示。大家可以选择 1G 大小左右的文件，再进行测试，整个过程可能会更加直观。

#### 分片上传功能分析

接下来，我们讲解下大文件分片上传是如何实现的，大家首先观察下面这个动图，了解下整个上传过程。

![enter image description here](https://images.gitbook.cn/f812b150-ab53-11e8-b6c8-d74329e78a24)

上传图片前先打开浏览器的控制台面板，点击 “ Network ” 来监控整个请求发起过程。上传图片发送的是 AJAX 请求，点击 “ XHR ” 将其他类型的请求过滤掉，之后选择需要上传的文件（依然选择了 55M 左右的一个文件)，点击 “ 开始上传 ” 按钮上传，之后就可看到文件上传成功并获取到其服务器路径。

接下来，我们研究 “ Network ” 下的所有请求。代码实现中，我将分片大小设置为 5M，此次上传的文件大小为 55M 多一点，因此切分后的分片数量为 12，同样的右侧 “ Network ” 面板共有 12 个请求。

点击某个请求可以看到它所包含的请求参数，比如文件名、文件大小等，这里我们要重点看下这几个参数：**chunks、chunk、guid**，chunks 是分片总数，chunk 是第几个分片，guid 是文件的唯一标示。比如点击第二个请求，可以看到 chunks 值为 12，chunk 为 1（下标从 0 开始），guid 值为 `aabfb5ed-6e12-41e7-be98-ecacbfe1545`。点击最后一个请求，其 chunk 值为 11。这些请求发送完成后，就可收到服务器返回的分片合并后的路径。

![enter image description here](https://images.gitbook.cn/002a87f0-ab54-11e8-b6c8-d74329e78a24)

了解了网络请求过程，我们再结合 Tomcat 目录的变化进一步了解 Java 实现大文件分片上传的过程。这里选择一个 374M 的视频文件进行整个测试过程。整个过程中会不断判断是否为最后一个分片，如果不是，则存储到 temp 目录下，通过上面动图可以看到该目录下的分片文件一直在增加；如果是最后一个分片，对其存储后开启所有分片的合并，并将新文件存储到 chunked 目录下，新文件生成后删除原来存储的所有分片数据，返回给浏览器新文件路径，整个过程就完成了。

![enter image description here](https://images.gitbook.cn/301eaf40-ab54-11e8-b6c8-d74329e78a24)

有一点请注意下，分片文件合成大文件的过程中，可能会发生错误，比如音乐、视频文件无法播放，安装包无法安装，文件有乱码等，所以在文件合并成功后，我们需要到 chunked 下找到对应的大文件，打开验证下是否可以正常使用。

### 断点续传

#### 什么是断点续传？

相信大家都使用过迅雷下载文件，比如文件在下载到 39.99% 时，由于某些原因，比如网速较慢，或者去打游戏，或者去看综艺节目，需要暂停文件的下载，之后在合适的时间再次启动下载，文件依然从 39.99% 处继续下载，而不是从头下载，这个过程就是我们所说的断点续传，它可以给用户提供很大的便利。

#### 断点续传什么样的效果？

断点续传支持从文件上次中断的地方开始传送数据，无需从头开始传送。我们可以从两个方面来理解断点续传：一方面是断点，另一方面是续传。文件传输过程中，程序会将该文件切分成多个部分，在某个时间点，任务被暂停，此时下载暂停的位置就是断点。续传就是当一个未完成的任务再次开始时，会从上次的断点继续传送。

断点续传常用于文件下载过程中。本节我们将为大家讲讲文件上传时的断点续传，所实现的 Demo 效果如下：

![enter image description here](https://images.gitbook.cn/3af35bf0-ab54-11e8-b6c8-d74329e78a24)

选择一个文件上传，点击 “ 暂停上传 ”，上传进度条暂停，再次点击 “ 断点续传 ” 时，进度条从上次暂停处继续增长。我进行了两次断点续传的操作，最终 690M 的视频成功上传。

#### 断点续传功能前端实现

注册 beforeSend 事件，文件上传前进行切片验证和参数填充：

```
        // 监听分块上传过程
        WebUploader.Uploader.register({
                "before-send": "beforeSend" //每个分片上传前
            },
            {//如果有分块上传，则每个分块上传之前调用此函数
                beforeSend: function (block) {
                    // block为分块数据。
                    // file为分块对应的file对象。
                    var file = block.file;
                    var fileName = file.name;
                    var deferred = WebUploader.Deferred();
                    $.ajax({
                        type: "POST",
                        url: "/upload/checkChunk",  //验证分片是否存在的请求
                        data: {
                            fileName: fileName,//文件名
                            guid: guid,
                            chunk: block.chunk,  //当前分块下标
                            chunks: block.chunks  //分块
                        },
                        cache: false,
                        async: false,
                        timeout: 2000,
                        success: function (response) {
                            if (response.resultCode == 200) {
                                //分块存在，跳过
                                console.log("已存在！跳过")
                                deferred.reject();
                            } else {
                                //分块不存在或不完整，重新发送该分块内容
                                console.log("不存在！上传分片")
                                deferred.resolve();
                            }
                        }
                    });
                    this.owner.options.formData.guid = guid;
                    this.owner.options.formData.fileName_ = file.name;
                    if (block.chunks > 1) { //文件大于chunksize分片上传
                        this.owner.options.formData.isChunked = true;
                    } else {
                        this.owner.options.formData.isChunked = false;
                    }
                    return deferred.promise();
                }
            });
```

暂停事件与续传事件注册，代码如下：

```
$("#startUpload").click(function () {
            uploader.upload();//上传
        });
        $("#stopUpload").click(function () {
            var status = $('#stopUpload').attr("status");
            if (status == "suspend") {
                $("#stopUpload").html("断点续传");
                $("#stopUpload").attr("status", "continuous");
                uploader.stop(true);
            } else {
                $("#stopUpload").html("暂停上传");
                $("#stopUpload").attr("status", "suspend");
                uploader.upload(uploader.getFiles("interrupt"));
            }
        });
```

#### 断点续传功能后端实现

增加了分片文件验证逻辑，其他功能与大文件分片上传功能相同：

```
    /**
     * @param chunks 当前所传文件的分片总数
     * @param chunk  当前所传文件的当前分片数
     * @return
     * @Description: 大文件上传前分片检查
     * @author: 13
     */
    @ResponseBody
    @RequestMapping(value = "/checkChunk")
    public Result checkChunk(HttpServletRequest request, String guid, Integer chunks, Integer chunk, String fileName) {
        try {
            String uploadDir = FileUtil.getRealPath(request);
            String ext = fileName.substring(fileName.lastIndexOf("."));
            // 判断文件是否分块
            if (chunks != null && chunk != null) {
                //文件路径
                StringBuilder tempFileName = new StringBuilder();
                tempFileName.append(uploadDir).append(File.separator).append("temp").append(File.separator).append(guid).append(File.separator).append(chunk).append(ext);
                File tempFile = new File(tempFileName.toString());
                //是否已存在分片,如果已存在分片则返回SUCCESS结果
                    if (tempFile.exists()) {
                     return ResultGenerator.genSuccessResult("分片已经存在！跳过此分片！");
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return ResultGenerator.genFailResult("error");
        }
        return ResultGenerator.genNullResult("不存在分片");
    }
```

#### 断点续传功能演示

`upload` 项目部署成功后，打开：`localhost:8080/webuploader-test3.html` 断点续传页面。我们选择一个稍大些的视频文件，上传过程中点击 “ 暂停上传 ” 按钮，此时触发上传暂停事件，按钮变为 “ 断点续传 ”，不再发送上传请求，要继续文件，需点击 “ 断点续传 ” 按钮，此时上传进度条又开始变化，并以上次暂停处为起点增长，并没有从头开始。成功上传后，页面中会出现服务器返回的文件路径。

![enter image description here](https://images.gitbook.cn/6a4c8650-ab55-11e8-b6c8-d74329e78a24)

根据返回的文件路径去 Tomcat 服务器目录进行验证，依次打开 upload 目录、chunked 目录，看是否存在刚上传的视频文件，并验证下文件是否能正确打开。整个验证过程如下图所示：

![enter image description here](https://images.gitbook.cn/9f5d93c0-ab55-11e8-b6c8-d74329e78a24)

#### 断点续传功能分析

![enter image description here](https://images.gitbook.cn/ac6fd460-ab55-11e8-b6c8-d74329e78a24)

结合上面动图，我们观察下断点续传功能的实现过程。上传图片前先打开浏览器的控制台面板，点击 “ Network ” 监控整个请求发起过程，上传图片发送的是 AJAX 请求，点击 “ XHR ” 将其他类型的请求过滤掉；之后选择需要上传的文件，点击 “ 开始上传 ” 按钮进行上传。

进度条到达 50% 左右时，点击 “ 暂停上传 ” 按钮，此时触发上传暂停事件，按钮变为 “ 断点续传 ”，接下来重点观察下右侧的网络请求面板，请求的 URL 与之前的多图上传和大文件上传有了明显差别。前两个功能的网络请求面板中只有图片上传的请求 URL，而添加了断点续传功能后，右侧的请求中有两个 URL 依次出现，分别为 `/upload/checkChunk` 和 `/upload/files`，即分片检查请求和文件上传请求。**注意，这两个请求 URL 是依次出现的。**也就是在请求分片验证成功后，紧跟着就会请求分片文件上传。

再次点开右侧的请求详情进行分析，点击其中一个 `/upload/checkChunk` 请求，服务端返回的是 “ 不存在分片 ”，之后继续发送上传分片文件请求。出现这种情况的功能逻辑如下：

- 分片上传前发送分片验证请求；
- 分片存在，则跳过此分片，即不再发送分片文件上传请求；
- 分片不存在，则继续发送分片文件上传请求；
- 以上过程重复执行直到文件上传处理完成。

接下来，我们再看下分片存在的场景下，右侧的请求是怎样的。首先清空右侧的请求面板便于此次请求的观察，接着前一个步骤继续，此时进度条在 50% 左右而按钮也变成了 “ 断点续传 ”，这时点击 “ 断点续传 ” 按钮，在进度条到达 65% 左右的时候，再次点击了 “ 暂停上传 ” 按钮，观察右侧的网络请求面板。此时的请求列表与刚刚发生了很大的变化，请求不再是交替依次出现，列表中的前一部分都是 `/upload/checkChunk` 请求，点击其中的几条记录查看，服务器返回的是 “ 分片已经存在！跳过此分片！”，说明分片都已上传到服务器，不需再次上传。而后面分片的上传方式依然同刚刚的场景一样，两个请求 URL 依次出现，先检查分片是否已存在，不存在则继续上传分片，直到文件上传成功。最终会收到服务器返回分片文件合并后的路径。

![enter image description here](https://images.gitbook.cn/ba938500-ab55-11e8-b6c8-d74329e78a24)

接着大家可以按我们之前讲解的方法，在 Tomcat 服务器下 temp 目录中观察文件上传、合并过程，及到 chunked 目录下，验证文件是否合并成功的过程。大家是不是觉得整个过程和大文件分片上传很类似，唯一的区别就是分片上传前增加了一个验证过程。

### 总结

文中涉及到的知识点比较多，考虑到对于某些小伙伴来说可能难以消化，因此在文中添加了代码分析和流程分析，可以参考的分析过程进行学习，另外，一定要结合源码和动手操作才能更加快速的掌握。

项目SSM版源码提取地址如下：

```
链接：https://pan.baidu.com/s/1SCfwvmfoCqE5EjP-atcb4Q 
密码：8osq
```

本教程主要讲解了单图上传、多图上传、大文件分片上传、断点续传四个功能点的实现和分析：

- 单图上传是如何通过 SpringMVC 进行实现的；
- 多图上传则是单图上传的升级版，本质上就是多次的单图上传处理；
- 而大文件上传处理是多文件上传的升级版，本质上就是将大文件切分成多个小文件并实现多文件上传；
- 断点续传则是大文件上传的升级版，多了一层分片是否已存在验证逻辑。

与之对应的项目源码子铭也已经完善并提供给各位朋友，希望朋友们可以自行对照着代码实践，对于这些知识点有一个自己的认识和体会。

- 图片管理模块对应的页面是 `picture.html`
- 多图上传对应的页面是 `webuploader-test.html`
- 大文件分片上传对应的页面是 `webuploader-test2.html`
- 断点上传对应的页面是 `webuploader-test3.html`