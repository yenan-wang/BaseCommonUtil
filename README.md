#基础工具类代码
##一、源码使用说明
###1.1、配置开关作用
配置开关位于gradle.properties文件中

`   # 发布到远程还是本地 
    isPublishRemote=false
    # 本地仓库依赖还是远程仓库依赖
    isRemote=true
    # 源码依赖还是本地仓库依赖 
    isLibSource=false
`
**isPublishRemote**代表本次发布aar，是发布到本地仓库，还是远程仓库；
为true，则发布到远程仓，即根目录下
为false，则发布到本地仓，即根目录“/repo”下
**注意：** 远程仓库位于根目录下，本地仓库位于根目录“/repo”下
本地仓库用于本地编码后验证发布是否正常使用，只有当确定本地编码一切正常，才可以置为true，并发布到远程仓库。
此外，isPublishRemote默认应为false，当修改为true发布后，应将该值恢复为false，更不能将为true的状态提交上去。
发布正式aar到根目录，用户使用的也是该目录下的aar

**isRemote**代表app模块依赖代码时，是远程依赖，还是本地依赖（本地依赖，是直接源码依赖common模块，还是依赖本地仓里的aar，由**isLibSource**决定）
为true，则直接依赖远程aar，即模拟线上用户的依赖，会从maven库（地址：https://raw.githubusercontent.com/yenan-wang/BaseCommonUtil/master）中下载，下载到的即为存放在根目录下的正式aar。

**isLibSource**代表本地依赖时，是直接源码依赖common模块，还是依赖本地仓里的aar；
为true，则直接依赖源码`implementation project(:common)`
为false，则依赖本地仓库里的aar `implementation com.ngb.wyn.base_util:common:0.0.1-LOCAL`

本地aar版本号固定为0.0.1-LOCAL

###1.2、源码开源声明
见根目录[LICENSE]: LICENSE

##二、接入方式
###2.1、项目根目录build.gradle添加maven地址
https://raw.githubusercontent.com/yenan-wang/BaseCommonUtil/master
`allprojects { 
    repositories {
    maven { url "https://raw.githubusercontent.com/yenan-wang/BaseCommonUtil/master" }
    }
}`

###2.2、引入依赖
`dependencies {
    implementation com.ngb.wyn.base_util:common:x.x.x
}`

###2.3、项目中即可使用
