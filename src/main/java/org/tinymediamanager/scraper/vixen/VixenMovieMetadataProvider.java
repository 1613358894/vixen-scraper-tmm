package org.tinymediamanager.scraper.vixen;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.core.entities.MediaRating;
import org.tinymediamanager.core.movie.MovieSearchAndScrapeOptions;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.MediaProviderInfo;
import org.tinymediamanager.scraper.MediaSearchResult;
import org.tinymediamanager.scraper.entities.MediaArtwork;
import org.tinymediamanager.scraper.entities.MediaType;
import org.tinymediamanager.scraper.exceptions.ScrapeException;
import org.tinymediamanager.scraper.interfaces.IMovieMetadataProvider;
import org.tinymediamanager.scraper.util.StrgUtils;

/**
 * The Vixen Movie Metadata Provider.
 *
 * @author YourName
 */
public class VixenMovieMetadataProvider implements IMovieMetadataProvider {
  private static final Logger     LOGGER           = LoggerFactory.getLogger(VixenMovieMetadataProvider.class);
  private static final String     ID               = "vixen";
  private static final String     BASE_URL         = "https://www.vixen.com";
  private static final String     SEARCH_URL       = BASE_URL + "/search?q=";
  private final MediaProviderInfo providerInfo;

  public VixenMovieMetadataProvider() {
    providerInfo = createMediaProviderInfo();
  }

  private MediaProviderInfo createMediaProviderInfo() {
    MediaProviderInfo info = new MediaProviderInfo(ID, "movie", "Vixen",
        "A scraper for Vixen movies",
        VixenMovieMetadataProvider.class.getResource("/org/tinymediamanager/scraper/vixen/vixen_logo.svg"));

    info.setResourceBundle(ResourceBundle.getBundle("org.tinymediamanager.scraper.vixen.messages"));

    // no configurable options here
    info.getConfig().load();

    return info;
  }

  @Override
  public MediaProviderInfo getProviderInfo() {
    return providerInfo;
  }

  @Override
  public boolean isActive() {
    return true;
  }

  /**
   * Extract search keyword from filename using regex
   * 
   * @param filename
   *          the filename to extract from
   * @return the extracted search keyword
   */
  private String extractSearchKeyword(String filename) {
    if (filename == null || filename.isEmpty()) {
      return "";
    }

    // Pattern: Vixen.YY.MM.DD.Name.More.XXX...
    // We want to extract "Name"
    Pattern pattern = Pattern.compile("\\d{2}\\.\\d{2}\\.\\d{2}\\.(\\w+)\\.");
    Matcher matcher = pattern.matcher(filename);
    
    if (matcher.find()) {
      return matcher.group(1);
    }
    
    // If pattern doesn't match, just return the filename
    return filename;
  }

  @Override
  public SortedSet<MediaSearchResult> search(MovieSearchAndScrapeOptions options) throws ScrapeException {
    LOGGER.debug("searching for: {}", options);
    
    SortedSet<MediaSearchResult> results = new TreeSet<>();

    String searchTerm = "";
    
    // 获取搜索关键词
    searchTerm = options.getSearchQuery();
    
    // 尝试从搜索查询中提取关键词
    if (!searchTerm.isEmpty()) {
      String extractedTerm = extractSearchKeyword(searchTerm);
      if (!extractedTerm.isEmpty()) {
        LOGGER.debug("Extracted search term from query: {}", extractedTerm);
        searchTerm = extractedTerm;
      }
    }
    
    // 如果搜索词仍为空，尝试使用标题
    if (searchTerm.isEmpty() && options.getSearchResult() != null && !options.getSearchResult().getTitle().isEmpty()) {
      searchTerm = options.getSearchResult().getTitle();
    }
    
    if (searchTerm.isEmpty()) {
      LOGGER.warn("Cannot search without search term");
      return results;
    }

    try {
      // 预处理搜索词 - 移除特殊字符和多余空格
      searchTerm = searchTerm.replaceAll("[^a-zA-Z0-9\\s]", " ").trim().replaceAll("\\s+", " ");
      
      // Encode search term for URL
      String encodedSearch = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8);
      String searchUrl = SEARCH_URL + encodedSearch;
      
      LOGGER.debug("Searching Vixen with URL: {}", searchUrl);
      
      // Connect to search page and get HTML
      Document doc = Jsoup.connect(searchUrl)
          .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
          .timeout(15000) // 增加超时时间，确保有足够时间加载
          .get();
      
      // 查找所有视频结果 - 使用更通用的选择器以捕获所有可能的结果
      Elements videoItems = doc.select("a[href^='/videos/']");
      
      // 如果没有找到结果，尝试其他选择器
      if (videoItems.isEmpty()) {
        videoItems = doc.select("a[href*='/video/']");
      }
      
      LOGGER.debug("Found {} potential results", videoItems.size());
      
      // 处理每个搜索结果
      for (Element videoItem : videoItems) {
        // 提取搜索结果信息
        String title = videoItem.select("h3").text();
        
        // 如果h3中没有标题，尝试其他可能包含标题的属性或元素
        if (title.isEmpty()) {
          title = videoItem.attr("title");
          if (title.isEmpty()) {
            title = videoItem.attr("alt");
            if (title.isEmpty()) {
              Element titleElement = videoItem.selectFirst("*[title]");
              if (titleElement != null) {
                title = titleElement.attr("title");
              }
            }
          }
        }
        
        // 如果仍然没有找到标题，尝试从URL中提取
        if (title.isEmpty()) {
          String href = videoItem.attr("href");
          if (href.contains("/videos/")) {
            // 从URL中提取并格式化标题
            String urlTitle = href.substring(href.lastIndexOf("/") + 1);
            // 将连字符替换为空格，并首字母大写
            title = urlTitle.replace("-", " ");
            // 首字母大写
            title = title.substring(0, 1).toUpperCase() + title.substring(1);
          }
        }
        
        // 如果标题仍为空，跳过此结果
        if (title.isEmpty()) {
          continue;
        }
        
        String videoUrl = videoItem.attr("href");
        // 确保URL是完整的
        if (!videoUrl.startsWith("http")) {
          videoUrl = BASE_URL + videoUrl;
        }
        
        // 避免重复的结果
        boolean isDuplicate = false;
        for (MediaSearchResult existingResult : results) {
          if (existingResult.getUrl().equals(videoUrl)) {
            isDuplicate = true;
            break;
          }
        }
        
        if (isDuplicate) {
          continue;
        }
        
        // 创建搜索结果
        MediaSearchResult searchResult = new MediaSearchResult(ID, MediaType.MOVIE);
        searchResult.setTitle(title);
        searchResult.setUrl(videoUrl);
        
        // 从URL中提取ID
        String id = videoUrl;
        if (id.contains("/videos/")) {
          id = id.substring(id.lastIndexOf("/") + 1);
        }
        searchResult.setId(id);
        
        // 计算搜索分数
        float score = 0.0f;
        
        // 提取搜索词的单词
        String[] searchWords = searchTerm.toLowerCase().split("\\s+");
        String titleLower = title.toLowerCase();
        
        // 基础分数：标题包含搜索词
        if (titleLower.contains(searchTerm.toLowerCase())) {
          score += 1.0f; // 完全匹配加满分
        } else {
          // 部分匹配：按匹配单词数计分
          int matchedWords = 0;
          for (String word : searchWords) {
            if (word.length() > 2 && titleLower.contains(word)) { // 忽略太短的单词
              matchedWords++;
            }
          }
          
          if (matchedWords > 0) {
            score += 0.5f + (0.5f * matchedWords / searchWords.length);
          }
        }
        
        // 额外加分：标题的开头部分匹配
        if (titleLower.startsWith(searchTerm.toLowerCase())) {
          score += 0.3f;
        }
        
        // 额外加分：URL中包含搜索词，可能表示更相关
        if (videoUrl.toLowerCase().contains(searchTerm.toLowerCase().replace(" ", "-"))) {
          score += 0.2f;
        }
        
        // 如果分数为0但有一些单词匹配，给一个最小分数
        if (score == 0.0f) {
          for (String word : searchWords) {
            if (word.length() > 3 && titleLower.contains(word)) {
              score = 0.1f;
              break;
            }
          }
        }
        
        // 设置分数
        searchResult.setScore(score);
        
        // 添加到结果集
        if (score > 0.0f) {
          results.add(searchResult);
          LOGGER.debug("Found search result: '{}' with score: {}", title, score);
        }
      }
      
      // 如果没有找到结果，但搜索词包含多个单词，尝试使用单个单词进行搜索
      if (results.isEmpty() && searchTerm.contains(" ")) {
        String[] words = searchTerm.split("\\s+");
        for (String word : words) {
          if (word.length() > 3) { // 只使用较长的单词，避免太泛泛的搜索
            // 创建一个新的媒体提供商来执行这个额外搜索，避免递归
            VixenMovieMetadataProvider tempProvider = new VixenMovieMetadataProvider();
            MovieSearchAndScrapeOptions tempOptions = new MovieSearchAndScrapeOptions();
            tempOptions.setSearchQuery(word);
            
            try {
              SortedSet<MediaSearchResult> fallbackResults = tempProvider.search(tempOptions);
              for (MediaSearchResult fallbackResult : fallbackResults) {
                // 降低分数以表示这是次要匹配
                fallbackResult.setScore(fallbackResult.getScore() * 0.7f);
                results.add(fallbackResult);
                LOGGER.debug("Added fallback result: '{}' with adjusted score: {}", 
                    fallbackResult.getTitle(), fallbackResult.getScore());
              }
            } catch (Exception e) {
              LOGGER.debug("Error during fallback search with word '{}': {}", word, e.getMessage());
            }
            
            // 只尝试第一个较长的单词，避免太多请求
            if (!results.isEmpty()) {
              break;
            }
          }
        }
      }
      
      LOGGER.debug("Returning {} total search results", results.size());
    }
    catch (IOException e) {
      LOGGER.error("Error searching for '{}': {}", searchTerm, e.getMessage());
      throw new ScrapeException(e);
    }

    return results;
  }

  @Override
  public MediaMetadata getMetadata(MovieSearchAndScrapeOptions options) throws ScrapeException {
    LOGGER.debug("getMetadata() - {}", options);
    
    MediaMetadata md = new MediaMetadata(ID);
    
    String url = options.getSearchResult().getUrl();
    
    if (url == null || url.isEmpty()) {
      throw new ScrapeException("No URL for metadata scraping");
    }
    
    try {
      // Connect to detail page and get HTML
      Document doc = Jsoup.connect(url)
          .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
          .timeout(10000)
          .get();
      
      // Extract title
      Element titleElement = doc.selectFirst("h1[data-test-component='VideoTitle']");
      if (titleElement != null) {
        String title = titleElement.text();
        md.setTitle(title);
        // 同时设置原始标题，确保NFO能正确导出标题
        md.setOriginalTitle(title);
        
        // (2) 检查标题是否以数字结尾，如果是，创建一个系列名称
        if (title.length() > 0 && Character.isDigit(title.charAt(title.length() - 1))) {
          // 找到最后一个非数字字符的位置
          int lastNonDigitIndex = title.length() - 1;
          while (lastNonDigitIndex >= 0 && Character.isDigit(title.charAt(lastNonDigitIndex))) {
            lastNonDigitIndex--;
          }
          
          // 提取系列名称（去掉末尾的数字）
          if (lastNonDigitIndex >= 0) {
            String setName = title.substring(0, lastNonDigitIndex + 1).trim();
            if (!setName.isEmpty()) {
              try {
                // 使用setCollectionName方法设置系列名称
                md.setCollectionName(setName);
                LOGGER.debug("Added movie to set: {}", setName);
              } catch (Exception e) {
                LOGGER.debug("Could not set collection name: {}", e.getMessage());
              }
            }
          }
        }
      }
      
      // Extract performers/actors
      Element performersElement = doc.selectFirst("div[data-test-component='VideoModels']");
      if (performersElement != null) {
        Elements performers = performersElement.select("a");
        for (Element performer : performers) {
          String name = performer.text();
          org.tinymediamanager.core.entities.Person person = new org.tinymediamanager.core.entities.Person(org.tinymediamanager.core.entities.Person.Type.ACTOR, name);
          
          // 为演员设置ID以确保在NFO中正确导出
          person.setId(ID, name.hashCode());
          
          // 设置演员在tMM中的唯一标识符，以便系统能正确匹配演员
          person.setName(name);
          
          // 设置演员的角色，如果没有特定角色，至少设置一个占位符
          // 这对于确保NFO文件包含完整的演员信息很重要
          person.setRole("Performer");
          
          // 如果能从Vixen页面提取演员的个人主页URL，也设置它
          // 这可以帮助tMM更准确地匹配演员
          String performerUrl = performer.attr("href");
          if (performerUrl != null && !performerUrl.isEmpty()) {
            if (!performerUrl.startsWith("http")) {
              performerUrl = BASE_URL + performerUrl;
            }
            // 设置演员主页URL作为ProfileUrl
            person.setProfileUrl(performerUrl);
            
            // 从演员个人页面获取照片URL
            String photoUrl = getActorPhotoUrl(performerUrl);
            if (!photoUrl.isEmpty()) {
              // 设置演员照片URL
              person.setThumbUrl(photoUrl);
              LOGGER.debug("Set thumb URL for actor {}: {}", name, photoUrl);
            }
          }
          
          // 重要：告诉tMM这个演员需要自动获取照片
          // 方法1：尝试设置特殊标志（如果API支持）
          try {
            java.lang.reflect.Method setAutoUpdateMethod = person.getClass().getMethod("setAutoUpdateEnabled", boolean.class);
            if (setAutoUpdateMethod != null) {
              setAutoUpdateMethod.invoke(person, true);
              LOGGER.debug("Enabled auto-update for actor {}", name);
            }
          } catch (Exception e) {
            LOGGER.debug("Could not set auto-update flag for actor {}: {}", name, e.getMessage());
          }
          
          md.addCastMember(person);
          LOGGER.debug("Added actor: {} with proper NFO attributes", name);
        }
      }
      
      // Extract release date
      Element dateElement = doc.selectFirst("span[data-test-component='ReleaseDateFormatted']");
      if (dateElement != null) {
        String dateString = dateElement.text();
        try {
          SimpleDateFormat parser = new SimpleDateFormat("MMMM dd, yyyy", Locale.US);
          Date releaseDate = parser.parse(dateString);
          md.setReleaseDate(releaseDate);
          
          // (2) 添加年份设置，解决年份缺失问题
          SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy", Locale.US);
          int year = Integer.parseInt(yearFormat.format(releaseDate));
          md.setYear(year);
          LOGGER.debug("Set year to {} from release date", year);
        }
        catch (ParseException e) {
          LOGGER.warn("Could not parse date: {}", e.getMessage());
        }
      }
      
      // Extract director
      Element directorElement = doc.selectFirst("span[data-test-component='DirectorText']");
      if (directorElement != null) {
        String directorName = directorElement.text();
        org.tinymediamanager.core.entities.Person director = new org.tinymediamanager.core.entities.Person(org.tinymediamanager.core.entities.Person.Type.DIRECTOR, directorName);
        // 为导演设置ID以确保在NFO中正确导出
        director.setId(ID, directorName.hashCode());
        md.addCastMember(director);
      }
      
      // Extract plot/description
      Element plotElement = doc.selectFirst("div.PlaybackContent__StyledDescription-sc-56y4pr-15 p");
      if (plotElement != null) {
        String plot = plotElement.text();
        md.setPlot(plot);
      }
      
      // (3) 获取实际播放时长
      Element runtimeElement = doc.selectFirst("span[data-test-component='RunLengthFormatted']");
      if (runtimeElement != null) {
        String runtimeStr = runtimeElement.text();
        // 解析格式为"分钟:秒"的时长
        String[] timeParts = runtimeStr.split(":");
        if (timeParts.length >= 2) {
          try {
            int minutes = Integer.parseInt(timeParts[0]);
            // 如果秒数超过30，则向上取整分钟数
            if (timeParts.length > 1 && Integer.parseInt(timeParts[1]) > 30) {
              minutes++;
            }
            // 设置运行时间（分钟）
            md.setRuntime(minutes);
            LOGGER.debug("Set runtime to {} minutes from {}", minutes, runtimeStr);
          } catch (NumberFormatException e) {
            LOGGER.warn("Could not parse runtime: {}", e.getMessage());
            // 使用默认值
            md.setRuntime(35);
          }
        } else {
          // 无法解析时，使用默认值
          md.setRuntime(35);
        }
      } else {
        // 未找到运行时间元素，使用默认值
        md.setRuntime(35);
      }
      
      // Extract artwork
      Element artworkElement = doc.selectFirst("img.ProgressiveImage__StyledImg-ptxr6s-2");
      if (artworkElement != null) {
        String artworkUrl = "";
        
        // Try to get high-res image from srcset
        String srcset = artworkElement.attr("srcset");
        if (!srcset.isEmpty()) {
          // Extract the highest resolution image URL from srcset
          String[] srcsetParts = srcset.split(", ");
          if (srcsetParts.length > 0) {
            String lastPart = srcsetParts[srcsetParts.length - 1];
            artworkUrl = lastPart.split(" ")[0];
          }
        }
        
        // Fallback to src if srcset didn't work
        if (artworkUrl.isEmpty()) {
          artworkUrl = artworkElement.attr("src");
        }
        
        if (!artworkUrl.isEmpty()) {
          // Add the poster to the metadata
          MediaArtwork ma = new MediaArtwork(ID, MediaArtwork.MediaArtworkType.POSTER);
          ma.setPreviewUrl(artworkUrl);
          
          // 语言可能需要设置，如果API支持
          try {
            ma.setLanguage(options.getLanguage().getLanguage());
          }
          catch (Exception e) {
            LOGGER.debug("Could not set language for artwork: {}", e.getMessage());
          }
          
          md.addMediaArt(ma);
        }
      }
      
      // (4) 设置正确的类型：直接设置为Porn
      try {
        // 尝试使用反射直接添加Porn类型
        try {
          java.lang.reflect.Method addGenreStringMethod = md.getClass().getMethod("addGenre", String.class);
          if (addGenreStringMethod != null) {
            addGenreStringMethod.invoke(md, "Porn");
            LOGGER.debug("Added 'Porn' genre using reflection");
          } else {
            // 回退到使用枚举
            md.addGenre(org.tinymediamanager.core.entities.MediaGenres.EROTIC);
            LOGGER.debug("Added FANTASY genre as fallback");
          }
        } catch (Exception e) {
          // 如果反射失败，使用枚举值
          md.addGenre(org.tinymediamanager.core.entities.MediaGenres.EROTIC);
          LOGGER.debug("Added FANTASY genre after reflection failed: {}", e.getMessage());
        }
      } catch (Exception e) {
        LOGGER.debug("Failed to set any genre: {}", e.getMessage());
      }
      
      // (5) 设置语言为English - 但不使用addValue方法
      try {
        // 使用setLanguage方法尝试设置语言为English
        java.lang.reflect.Method setLanguageMethod = md.getClass().getMethod("setLanguage", String.class);
        setLanguageMethod.invoke(md, "en");  // 使用"en"而不是"English"
        LOGGER.debug("Set language to 'en' using setLanguage");
      } catch (Exception e1) {
        // 如果setLanguage方法失败，记录错误并继续
        LOGGER.debug("Could not set language: {}", e1.getMessage());
      }
      
      // 设置内容评级
      md.addCertification(org.tinymediamanager.scraper.entities.MediaCertification.US_NC17);
      
      // (1) 获取实际评分值
      Element ratingElement = doc.selectFirst("span[data-test-component='RatingNumber']");
      if (ratingElement != null) {
        String ratingStr = ratingElement.text();
        try {
          float ratingValue = Float.parseFloat(ratingStr);
          // 创建评分对象
          MediaRating rating = new MediaRating(ID);
          rating.setMaxValue(10);
          rating.setRating(ratingValue);
          rating.setVotes(1); // 默认投票数为1
          md.addRating(rating);
          LOGGER.debug("Set rating to {} from webpage", ratingValue);
        } catch (NumberFormatException e) {
          LOGGER.warn("Could not parse rating: {}", e.getMessage());
          // 使用默认评分
          MediaRating defaultRating = new MediaRating(ID);
          defaultRating.setMaxValue(10);
          defaultRating.setRating(7.5f);
          defaultRating.setVotes(1);
          md.addRating(defaultRating);
        }
      } else {
        // 未找到评分元素，使用默认评分
        MediaRating defaultRating = new MediaRating(ID);
        defaultRating.setMaxValue(10);
        defaultRating.setRating(7.5f);
        defaultRating.setVotes(1);
        md.addRating(defaultRating);
      }
      
      // 设置元数据ID，确保能被正确识别
      md.setId(ID, url.substring(url.lastIndexOf("/") + 1));
    }
    catch (IOException e) {
      LOGGER.error("Error getting metadata: {}", e.getMessage());
      throw new ScrapeException(e);
    }
    
    return md;
  }

  /**
   * 从演员个人页面获取演员照片URL
   * 
   * @param performerUrl 演员个人页面URL
   * @return 照片URL，如果获取失败则返回空字符串
   */
  private String getActorPhotoUrl(String performerUrl) {
    if (performerUrl == null || performerUrl.isEmpty()) {
      return "";
    }
    
    try {
      LOGGER.debug("Getting actor photo from: {}", performerUrl);
      
      // 连接到演员个人页面
      Document doc = Jsoup.connect(performerUrl)
          .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
          .timeout(10000)
          .get();
      
      // 使用选择器获取图片元素
      // 主选择器 - 基于用户提供的信息
      Element imgElement = doc.selectFirst("#__next > main > section > section > aside > div > picture > img");
      
      // 备选选择器 - 防止页面结构变化
      if (imgElement == null) {
        imgElement = doc.select("img[alt*='" + performerUrl.substring(performerUrl.lastIndexOf("/") + 1).replace("-", " ") + "']").first();
      }
      
      if (imgElement == null) {
        // 再尝试更通用的选择器
        imgElement = doc.select("img.ProgressiveImage__StyledImg-ptxr6s-2").first();
      }
      
      if (imgElement != null) {
        LOGGER.debug("Found image element with outerHTML: {}", imgElement.outerHtml());
        
        // 优先使用srcset中的高分辨率图片
        String srcset = imgElement.attr("srcset");
        if (srcset != null && !srcset.isEmpty()) {
          LOGGER.debug("Found srcset: {}", srcset);
          
          // 提取包含"2x"的URL部分
          int index2x = srcset.lastIndexOf(" 2x");
          if (index2x > 0) {
            // 从最后一个逗号到2x之前是我们需要的URL
            int lastCommaIndex = srcset.lastIndexOf(",", index2x);
            String highResUrl;
            
            if (lastCommaIndex > 0) {
              // 有逗号分隔，取最后一部分
              highResUrl = srcset.substring(lastCommaIndex + 1, index2x).trim();
            } else {
              // 没有逗号，可能只有一个URL
              highResUrl = srcset.substring(0, index2x).trim();
              // 检查是否有空格（表示分辨率标记）
              int spaceIndex = highResUrl.lastIndexOf(" ");
              if (spaceIndex > 0) {
                highResUrl = highResUrl.substring(0, spaceIndex).trim();
              }
            }
            
            // 替换HTML实体字符
            highResUrl = highResUrl.replace("&amp;", "&");
            
            LOGGER.debug("Extracted high-res (2x) photo URL: {}", highResUrl);
            return highResUrl;
          }
          
          // 如果没有2x版本，尝试解析完整的srcset
          String[] srcsetParts = srcset.split(", ");
          if (srcsetParts.length > 0) {
            // 取最后一个部分（通常是最高分辨率）
            String lastPart = srcsetParts[srcsetParts.length - 1];
            // 提取URL部分（去除分辨率标记）
            int spaceIndex = lastPart.lastIndexOf(" ");
            String highResUrl = spaceIndex > 0 ? lastPart.substring(0, spaceIndex).trim() : lastPart.trim();
            
            // 替换HTML实体字符
            highResUrl = highResUrl.replace("&amp;", "&");
            
            LOGGER.debug("Extracted highest-res photo URL from srcset: {}", highResUrl);
            return highResUrl;
          }
        }
        
        // 如果srcset没有有效内容，回退到src
        String src = imgElement.attr("src");
        if (src != null && !src.isEmpty()) {
          // 替换HTML实体字符
          src = src.replace("&amp;", "&");
          
          LOGGER.debug("Found actor photo from src: {}", src);
          return src;
        }
      }
      
      LOGGER.debug("Could not find actor photo");
      return "";
    } catch (Exception e) {
      LOGGER.error("Error getting actor photo: {}", e.getMessage(), e);
      return "";
    }
  }
} 