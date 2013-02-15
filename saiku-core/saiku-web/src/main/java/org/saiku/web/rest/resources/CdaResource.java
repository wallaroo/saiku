package org.saiku.web.rest.resources;



import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import pt.webdetails.cda.CdaEngine;
import pt.webdetails.cda.exporter.Exporter;
import pt.webdetails.cda.exporter.ExporterEngine;
import pt.webdetails.cda.query.QueryOptions;
import pt.webdetails.cda.settings.CdaSettings;
import pt.webdetails.cda.settings.SettingsManager;

import com.sun.jersey.api.core.HttpRequestContext;


@Component
@Path("/cda")
public class CdaResource implements Serializable {

	public static final String PLUGIN_NAME = "cda";
	private static final long serialVersionUID = 1L;
	private static final String EDITOR_SOURCE = "/editor/editor.html";
	private static final String EXT_EDITOR_SOURCE = "/editor/editor-cde.html";
	private static final String PREVIEWER_SOURCE = "/previewer/previewer.html";
	private static final String CACHE_MANAGER_PATH = "system/" + PLUGIN_NAME + "/cachemanager/cache.html";
	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int DEFAULT_START_PAGE = 0;
	private static final String PREFIX_PARAMETER = "param";
	private static final String PREFIX_SETTING = "setting";
	private static final String JSONP_CALLBACK = "callback";
	public static final String ENCODING = "UTF-8";

	private static final Logger log = LoggerFactory.getLogger(CdaResource.class);
	
	@Autowired
	private BasicRepositoryResource2 repository; 


	
	// NOTES:
	// file = cda file, no "solution" or "path" parameter
	@POST
	@Path("/doQuery")
	public Response doQuery(
			@QueryParam("file") String file,
			@QueryParam("pageSize") @DefaultValue("0") Integer pageSize,
			@QueryParam("pageStart") @DefaultValue("0") Integer pageStart,
			@QueryParam("paginateQuery") @DefaultValue("false") Boolean paginate,
			@QueryParam("bypassCache") @DefaultValue("false") Boolean bypassCache,
			@QueryParam("outputType") @DefaultValue("json") String outputType,
			// really set this to <blank>? check with queryoptions
			@QueryParam("dataAccessId") String dataAccessId,
			@QueryParam("outputIndexId") @DefaultValue("1") Integer outputIndexId,
			// FIXME ---- sortBy = array?
			@QueryParam("sortBy") List<String> sortBy,
			@QueryParam("wrapItUp") String wrapItUp,
			@QueryParam(JSONP_CALLBACK) String callback,
			@Context HttpServletRequest req,
			@Context HttpServletResponse resp

			) 
	{
		try {
			OutputStream out = resp.getOutputStream();
			final CdaEngine engine = CdaEngine.getInstance();
			final QueryOptions queryOptions = new QueryOptions();
			// should we use relative path here?
			final CdaSettings cdaSettings = SettingsManager.getInstance().parseSettingsFile(file);
			
			// does the user want paging?
			if (pageSize > 0 || pageStart > 0 || paginate) {
		      if (pageSize > Integer.MAX_VALUE || pageStart > Integer.MAX_VALUE) {
		        throw new ArithmeticException("Paging values too large");
		      }
		      queryOptions.setPaginate(true);
		      queryOptions.setPageSize(pageSize > 0 ? (int) pageSize : paginate ? DEFAULT_PAGE_SIZE : 0);
		      queryOptions.setPageStart(pageStart > 0 ? (int) pageStart : paginate ? DEFAULT_START_PAGE : 0);
		    }
			
		    // Support for bypassCache (we'll maintain the name we use in CDE
			if (bypassCache) {
				queryOptions.setCacheBypass(bypassCache);
			}
			// we could validate this with OutputType?
			queryOptions.setOutputType(outputType);
		    queryOptions.setDataAccessId(dataAccessId);
		    queryOptions.setOutputIndexId(outputIndexId);
		    ArrayList<String> ar = new ArrayList<String>();
		    ar.addAll(sortBy);
		    if (sortBy != null) {
		    	queryOptions.setSortBy(ar);
		    }
		    
		    Map<String, String[]> params = req.getParameterMap();
		    // ... and the query parameters
		    // We identify any pathParams starting with "param" as query parameters and extra settings prefixed with "setting"


		    // FIXME we should probably be able to have array params as well
		    for (String param : req.getParameterMap().keySet())
		    {
		    	System.out.println("Parameter: " + param);
		    	String value = req.getParameter(param);
		    	System.out.println("Value: " + value);
		    	if (param.startsWith(PREFIX_PARAMETER))
		    	{

		    		queryOptions.addParameter(param.substring(PREFIX_PARAMETER.length()), value);
		    	}
		    	else if (param.startsWith(PREFIX_SETTING))
		    	{
		    		queryOptions.addSetting(param.substring(PREFIX_SETTING.length()), value);
		    	}
		    }
		    
		    if(StringUtils.isNotBlank(wrapItUp)) {
		      String uuid = engine.wrapQuery(out, cdaSettings, queryOptions);
		      log.debug("doQuery: query wrapped as " + uuid);
		      writeOut(out, uuid);
		      return Response.ok().build();
		    }
		    
		    // we'll allow for the special "callback" param to be used, and passed as settingcallback to jsonp exports
		    if (StringUtils.isNotBlank(callback))
		    {
		      queryOptions.addSetting(JSONP_CALLBACK, callback);
		    }
	
		    Exporter exporter = ExporterEngine.getInstance().getExporter(queryOptions.getOutputType(), queryOptions.getExtraSettings());
		    
		    String attachmentName = exporter.getAttachmentName();
		    String mimeType = (attachmentName == null) ? null : getMimeType(attachmentName);
		    if(StringUtils.isEmpty(mimeType)){
		      mimeType = exporter.getMimeType();
		    }
		    
		    if (req != null)
		    {
		      setResponseHeaders(resp, mimeType, attachmentName);
		    }
		    // Finally, pass the query to the engine
		    engine.doQuery(out, cdaSettings, queryOptions);

		    
		    return Response.ok().build();
			
		} catch(Exception e) {
			e.printStackTrace();
			return Response.serverError().entity(e.getStackTrace()).status(Response.Status.INTERNAL_SERVER_ERROR).build();
		}
		
	}
	
	
	// THE BELOW IS ALL CPF AND SHOULD BE A UTIL CLASS
	
    protected void setResponseHeaders(HttpServletResponse response, final String mimeType){
        setResponseHeaders(response, mimeType, 0, null);
      }
      
      protected void setResponseHeaders(HttpServletResponse response, final String mimeType, final String attachmentName){
        setResponseHeaders(response, mimeType, 0, attachmentName);
      }
      
      protected void setResponseHeaders(HttpServletResponse response, final String mimeType, final int cacheDuration, final String attachmentName)
      {
        // Make sure we have the correct mime type
        
//        final IMimeTypeListener mimeTypeListener = outputHandler.getMimeTypeListener();
//        if (mimeTypeListener != null)
//        {
//          mimeTypeListener.setMimeType(mimeType);
//        }
//        
//        final HttpServletResponse response = getResponse();

        if (response == null)
        {
          log.warn("Parameter 'httpresponse' not found!");
          return;
        }

        response.setHeader("Content-Type", mimeType);

        if (attachmentName != null)
        {
          response.setHeader("content-disposition", "attachment; filename=" + attachmentName);
        } // Cache?

        if (cacheDuration > 0)
        {
          response.setHeader("Cache-Control", "max-age=" + cacheDuration);
        }
        else
        {
          response.setHeader("Cache-Control", "max-age=0, no-store");
        }
      }
      
      /**
       * Write to OutputStream using defined encoding.
       * @param out
       * @param contents
       * @throws IOException
       */
      protected void writeOut(OutputStream out, String contents) throws IOException {
    	// TODO do we need the encoding pluggable? PluginSettings.getEncoding()
        IOUtils.write(contents, out, ENCODING);
      }
      

      public enum FileType
      {
        JPG, JPEG, PNG, GIF, BMP, JS, CSS, HTML, HTM, XML,
        SVG, PDF, TXT, DOC, DOCX, XLS, XLSX, PPT, PPTX;
        
        public static FileType parse(String value){
          return valueOf(StringUtils.upperCase(value));
        }
      }
      
      public static class MimeType {
        public static final String CSS = "text/css";
        public static final String JAVASCRIPT = "text/javascript";
        public static final String PLAIN_TEXT = "text/plain";
        public static final String HTML = "text/html";
        public static final String XML = "text/xml";
        public static final String JPEG = "img/jpeg";
        public static final String PNG = "image/png";
        public static final String GIF = "image/gif";
        public static final String BMP = "image/bmp";
        public static final String JSON = "application/json";
        public static final String PDF = "application/pdf";

        public static final String DOC = "application/msword";
        public static final String DOCX = "application/msword";
        
        public static final String XLS = "application/msexcel";      
        public static final String XLSX = "application/msexcel";
        
        public static final String PPT = "application/mspowerpoint";
        public static final String PPTX = "application/mspowerpoint";
      }
      
      protected static final EnumMap<FileType, String> mimeTypes = new EnumMap<FileType, String>(FileType.class);
      
      static
      {
        /*
         * Image types
         */
        mimeTypes.put(FileType.JPG, MimeType.JPEG);
        mimeTypes.put(FileType.JPEG, MimeType.JPEG);
        mimeTypes.put(FileType.PNG, MimeType.PNG);
        mimeTypes.put(FileType.GIF, MimeType.GIF);
        mimeTypes.put(FileType.BMP, MimeType.BMP);

        /*
         * HTML (and related) types
         */
        // Deprecated, should be application/javascript, but IE doesn't like that
        mimeTypes.put(FileType.JS, MimeType.JAVASCRIPT);
        mimeTypes.put(FileType.HTM, MimeType.HTML);
        mimeTypes.put(FileType.HTML, MimeType.HTML);
        mimeTypes.put(FileType.CSS, MimeType.CSS);
        mimeTypes.put(FileType.XML, MimeType.XML);
        mimeTypes.put(FileType.TXT, MimeType.PLAIN_TEXT);
      }
      
      protected String getMimeType(String fileName){
        String[] fileNameSplit = StringUtils.split(fileName, '.');// fileName.split("\\.");
        try{
          return getMimeType(FileType.valueOf(fileNameSplit[fileNameSplit.length - 1].toUpperCase()));
        }
        catch(Exception e){
          log.warn("Unrecognized extension for file name " + fileName);
          return "";
        }
      }
      
      protected String getMimeType(FileType fileType){
        if(fileType == null) return "";
        String mimeType = mimeTypes.get(fileType);
        return mimeType == null ? "" : mimeType;
      }

}
