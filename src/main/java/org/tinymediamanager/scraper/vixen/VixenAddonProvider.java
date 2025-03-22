package org.tinymediamanager.scraper.vixen;

import java.util.ArrayList;
import java.util.List;

import org.kohsuke.MetaInfServices;
import org.tinymediamanager.scraper.interfaces.IMediaProvider;
import org.tinymediamanager.scraper.spi.IAddonProvider;

/**
 * The Vixen Addon Provider - the main connector between the custom scraper and tinyMediaManager
 * 
 * @author YourName
 */
@MetaInfServices
public class VixenAddonProvider implements IAddonProvider {

  @Override
  public List<Class<? extends IMediaProvider>> getAddonClasses() {
    List<Class<? extends IMediaProvider>> addons = new ArrayList<>();
    
    // add the movie metadata provider
    addons.add(VixenMovieMetadataProvider.class);
    
    return addons;
  }
} 