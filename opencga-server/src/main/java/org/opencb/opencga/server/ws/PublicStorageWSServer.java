package org.opencb.opencga.server.ws;

import com.google.common.base.Splitter;
import com.google.common.io.Files;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.opencb.commons.utils.FileUtils;
import org.opencb.opencga.account.db.AccountManagementException;
import org.opencb.opencga.lib.common.Config;
import org.opencb.opencga.lib.common.StringUtils;

/**
 *
 * @author Cristina Yenyxe Gonzalez Garcia <cgonzalez@cipf.es>
 */
@Path("/public/storage/{bucketId}/{objectId}")
@Produces(MediaType.APPLICATION_JSON)
public class PublicStorageWSServer extends GenericWSServer {

    private String bucketId;
    private java.nio.file.Path objectId;
    
    public PublicStorageWSServer(UriInfo uriInfo, HttpServletRequest httpServletRequest) throws IOException {
        super(uriInfo, httpServletRequest);
    }
    

    public PublicStorageWSServer(@Context UriInfo uriInfo, @Context HttpServletRequest httpServletRequest,
                                @DefaultValue("") @PathParam("bucketId") String bucketId,
                                @DefaultValue("") @PathParam("objectId") String objectId) 
            throws IOException, AccountManagementException {
        super(uriInfo, httpServletRequest);
        this.bucketId = bucketId;
        this.objectId = StringUtils.parseObjectId(objectId);
    }
    
    
    // TODO for now, only region filter allowed
    @GET
    @Path("/fetch")
    public Response fetchData(@DefaultValue("") @PathParam("objectId") String objectIdFromURL,
                           @DefaultValue("") @QueryParam("region") String regionStr) {
        try {
            Properties storageProperties = Config.getStorageProperties(Config.getGcsaHome());
            if (storageProperties == null) {
                return createErrorResponse("\"storage.properties\" file not found!");
            }
            
            // 1: Check if the bucket is in the list of allowed folders
            if (!isBucketAllowed(storageProperties)) {
                throw new IllegalArgumentException("Reading the files inside the requested folder is forbidden");
            }
            
            // 2: Check if the path corresponding to alias folder + filename exists
            if (!storageProperties.containsKey("OPENCGA.LOCAL.FOLDERS." + bucketId)) {
                throw new IllegalStateException("There is no path defined for the requested folder ID");
            }
            
            String bucketPathName = storageProperties.getProperty("OPENCGA.LOCAL.FOLDERS." + bucketId, "");
            System.out.println("* Bucket path name = " + bucketPathName);
            java.nio.file.Path objectPath = Paths.get(bucketPathName + "/" + objectId);
            System.out.println("* Object path name = " + bucketPathName);
            FileUtils.checkFile(objectPath); // Check file exists and is readable
            
            // 3: Check if the file format is allowed for that alias
            if (!isFileFormatAllowed(Files.getFileExtension(objectPath.toString()), storageProperties)) {
                throw new IllegalArgumentException("Reading this file format inside the requested folder is forbidden");
            }
            
            // 4: Launch queries
            List<String> regions = Splitter.on(',').splitToList(regionStr);
            List<String> results = new ArrayList<>();
            
            for (String region : regions) {
                results.add(cloudSessionManager.fetchData(objectPath, Files.getFileExtension(objectPath.toString()), region, params));
            }
            return createOkResponse(results.toString());
        } catch (Exception e) {
            logger.error(e.toString());
            return createErrorResponse(e.getMessage());
        }
    }


    private boolean isBucketAllowed(Properties storageProperties) throws IllegalStateException {
        if (!storageProperties.containsKey("OPENCGA.LOCAL.FOLDERS.ALLOWED")) {
            throw new IllegalStateException("List of folders with read-access not available. Please check your \"storage.properties\" file.");
        }
        String[] buckets = storageProperties.getProperty("OPENCGA.LOCAL.FOLDERS.ALLOWED", "").split(",");
        boolean bucketAllowed = false;
        for (String bucket : buckets) {
            if (bucket.equalsIgnoreCase(bucketId)) {
                bucketAllowed = true;
                break;
            }
        }
        
        return bucketAllowed;
    }

    private boolean isFileFormatAllowed(String fileFormat, Properties storageProperties) {
        String[] fileFormatsAllowed = new String[0]; // No formats allowed if none specified
        if (storageProperties.containsKey("OPENCGA.LOCAL.EXTENSIONS." + bucketId)) {
            fileFormatsAllowed = storageProperties.getProperty("OPENCGA.LOCAL.EXTENSIONS." + bucketId, "").split(",");
        } else if (storageProperties.containsKey("OPENCGA.LOCAL.EXTENSIONS.ALLOWED")) {
            fileFormatsAllowed = storageProperties.getProperty("OPENCGA.LOCAL.EXTENSIONS.ALLOWED", "").split(",");
        }
        
        logger.debug("Bucket extensions: " + storageProperties.getProperty("OPENCGA.LOCAL.EXTENSIONS." + bucketId));
        logger.debug("Global extensions: " + storageProperties.getProperty("OPENCGA.LOCAL.EXTENSIONS.ALLOWED"));
        
        boolean formatAllowed = false;
        for (String format : fileFormatsAllowed) {
            if (format.equalsIgnoreCase(fileFormat)) {
                formatAllowed = true;
                break;
            }
        }
        return formatAllowed;
    }
}
