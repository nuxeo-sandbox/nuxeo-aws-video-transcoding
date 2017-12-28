/*
 * (C) Copyright 2006-2017 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 * Contributors:
 *     anechaev
 *     michael vachette
 */

package org.nuxeo.labs.aws.video.transcoding.listeners;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jettison.json.JSONObject;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.PostCommitFilteringEventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.storage.sql.S3BinaryManager;
import org.nuxeo.ecm.platform.video.VideoHelper;
import org.nuxeo.ecm.platform.video.service.VideoService;
import org.nuxeo.lambda.core.LambdaInput;
import org.nuxeo.lambda.core.LambdaService;
import org.nuxeo.runtime.api.Framework;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import static org.nuxeo.ecm.platform.video.VideoConstants.VIDEO_CHANGED_EVENT;
import static org.nuxeo.ecm.platform.video.VideoConstants.VIDEO_FACET;

public class VideoChangedListener implements PostCommitFilteringEventListener {

    private static final Log log = LogFactory.getLog(VideoChangedListener.class);

    @Override
    public void handleEvent(EventBundle eventBundle) {
        for (Event event : eventBundle) {
            if (VIDEO_CHANGED_EVENT.equals(event.getName())) {
                handleEvent(event);
            }
        }
    }

    private void handleEvent(Event event) {
        DocumentEventContext ctx = (DocumentEventContext) event.getContext();
        if (ctx == null) {
            return;
        }

        DocumentModel doc = ctx.getSourceDocument();

        if (!doc.hasFacet(VIDEO_FACET) || doc.isVersion() || doc.isProxy()) {
            return;
        }

        Property content = doc.getProperty("file:content");
        Blob blob = (Blob) content.getValue();

        if (blob == null) {
            return;
        }

        //filter non S3 blobs
        BlobManager blobManager = Framework.getService(BlobManager.class);
        BlobProvider blobProvider = blobManager.getBlobProvider(blob);
        if (!(blobProvider instanceof S3BinaryManager)) {
            // use default services
            VideoHelper.updateStoryboard(doc, blob);
            try {
                VideoHelper.updatePreviews(doc, blob);
            } catch (IOException e) {
                log.error(e);
            }
            VideoService videoService = Framework.getService(VideoService.class);
            videoService.launchAutomaticConversions(doc);
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
        input.put("videoInfo", info);

        try {
            service.scheduleCall("aws-video-conversions", params, new LambdaInput(input));
        } catch (Exception e) {
            throw new NuxeoException(e);
        }
    }

    @Override
    public boolean acceptEvent(Event event) {
        return VIDEO_CHANGED_EVENT.equals(event.getName());
    }

}