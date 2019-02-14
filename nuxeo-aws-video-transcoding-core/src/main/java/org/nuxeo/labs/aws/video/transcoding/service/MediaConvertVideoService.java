package org.nuxeo.labs.aws.video.transcoding.service;

import org.json.JSONObject;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.storage.sql.S3BinaryManager;
import org.nuxeo.ecm.platform.video.service.VideoServiceImpl;
import org.nuxeo.lambda.core.LambdaInput;
import org.nuxeo.lambda.core.LambdaService;
import org.nuxeo.runtime.api.Framework;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class MediaConvertVideoService extends VideoServiceImpl {

    @Override
    public void launchAutomaticConversions(DocumentModel doc) {

        Property content = doc.getProperty("file:content");
        Blob blob = (Blob) content.getValue();

        if (blob == null) {
            return;
        }

        //filter non S3 blobs
        BlobManager blobManager = Framework.getService(BlobManager.class);
        BlobProvider blobProvider = blobManager.getBlobProvider(blob);
        if (!(blobProvider instanceof S3BinaryManager)) {
            super.launchAutomaticConversions(doc);
            return;
        }

        Map<String, Serializable> params = new HashMap<>();
        params.put("docId", doc.getId());
        params.put("repository", doc.getRepositoryName());

        LambdaService service = Framework.getService(LambdaService.class);

        Map<String, Serializable> input = new HashMap<>();
        input.put("key", blob.getDigest());
        input.put("bucket", Framework.getProperty("nuxeo.s3storage.bucket"));
        input.put("filename", blob.getFilename());
        Map<String,Serializable> videoInfo = (Map<String, Serializable>) doc.getPropertyValue("video:info");
        JSONObject info = new JSONObject(videoInfo);
        input.put("videoInfo", (Serializable) info);

        try {
            service.scheduleCall("aws-video-conversions", params, new LambdaInput(input));
        } catch (Exception e) {
            throw new NuxeoException(e);
        }

    }
}
