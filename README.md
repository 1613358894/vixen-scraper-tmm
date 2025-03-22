# Vixen 元数据刮削插件 (tinyMediaManager)

一个用于tinyMediaManager的Vixen元数据刮削插件，提供完整的电影信息和高分辨率演员照片支持。

## 功能特点

- **完整元数据获取**：从Vixen网站获取电影标题、描述、评分、导演和发布日期
- **智能系列名称**：当电影标题以数字结尾时，自动设置正确的系列名称
- **高质量图片**：
  - 获取高分辨率电影封面
  - 直接从演员个人页面抓取高分辨率演员照片
- **正确分类**：
  - 将类型设置为"Porn"
  - 将语言设置为"English"
- **准确时长**：精确提取影片实际播放时长

## 安装方法

1. 下载最新的 [vixen-scraper-1.0.jar](https://github.com/1613358894/vixen-scraper-tmm/releases/latest/download/vixen-scraper-1.0.jar)
2. 将JAR文件复制到tinyMediaManager的addons目录：
   - Windows: `C:\Program Files\tinyMediaManager\addons` 或 `安装目录\addons`
   - Mac: `/Applications/tinyMediaManager.app/Contents/Resources/app/addons`
   - Linux: `/opt/tinyMediaManager/addons`
3. 重启tinyMediaManager

## 使用方法

1. 在tinyMediaManager中，确保已启用Vixen刮削器：
   - 选择"设置" > "影片设置" > "刮削器" 
   - 确保"Vixen"选项被勾选
   
2. 为获得最佳演员图片体验：
   - 选择"设置" > "影片设置" > "NFO设置"
   - 勾选"将演员图片写入NFO"选项
   
3. 刮削电影：
   - 选择一部电影
   - 点击"搜索和刮削" > "搜索并刮削选中的电影"
   - 在搜索对话框中选择"Vixen"作为刮削源
   - 搜索并选择正确的匹配项

## 技术亮点

- **演员照片提取**：
  - 使用多层选择器策略确保稳定获取演员照片
  - 优先提取高分辨率(2x)版本图片
  - 自动处理查询参数和特殊字符
  
- **可靠性增强**：
  - 详细的日志记录，便于调试
  - 多重备选策略确保最大兼容性
  - 针对API变化的适应性处理

## 构建从源代码

如果您想自己编译插件：

```bash
mvn clean package
```

编译后的JAR文件将位于`target`目录中。

## 技术要求

- Java 8+
- tinyMediaManager v4或v5

## 许可证

MIT License - 详见[LICENSE](LICENSE)文件 