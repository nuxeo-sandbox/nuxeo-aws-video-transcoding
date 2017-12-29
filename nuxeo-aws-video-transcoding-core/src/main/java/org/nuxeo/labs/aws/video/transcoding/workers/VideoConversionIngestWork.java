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
package org.nuxeo.labs.aws.video.transcoding.workers;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.blob.BlobInfo;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.work.AbstractWork;
import org.nuxeo.ecm.platform.mimetype.interfaces.MimetypeRegistry;
import org.nuxeo.ecm.platform.video.TranscodedVideo;
import org.nuxeo.ecm.platform.video.VideoHelper;
import org.nuxeo.ecm.platform.video.VideoInfo;
import org.nuxeo.runtime.api.Framework;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;


public class VideoConversionIngestWork extends AbstractWork {

    private static final Log log = LogFactory.getLog(VideoConversionIngestWork.class);

    protected final JSONArray conversions;

    public VideoConversionIngestWork(String repository, String docId, JSONArray conversions) {
        super(repository + ":" + docId + ":ingest-conversions");
        setDocument(repository, docId);
        this.conversions = conversions;
    }

    @Override
    public String getTitle() {
        return id;
    }

    @Override
    public void work() {
        setStatus("Starting");

        setProgress(Progress.PROGRESS_INDETERMINATE);

        openSystemSession();

        DocumentModel doc = session.getDocument(new IdRef(docId));
        Blob blob = (Blob) doc.getPropertyValue("file:content");

        BlobManager blobManager = Framework.getService(BlobManager.class);
        BlobProvider blobProvider = blobManager.getBlobProvider(blob);

        List<Map<String, Serializable>> transcodedVideos = new ArrayList<>();

        for (int i = 0; i < conversions.length(); i++) {
            try {
                JSONObject object = conversions.getJSONObject(i);
                processConversion(object,transcodedVideos,blobProvider,blob.getFilename());
            } catch (JSONException | IOException e) {
                log.error(e);
            }
        }

        Collections.sort(transcodedVideos, (left, right) -> (int)((Long)left.get("height") - (Long)right.get("height")));
        doc.setPropertyValue("vid:transcodedVideos", (Serializable) transcodedVideos);
        session.saveDocument(doc);

        // compute storyboard and preview from lowest resolution transcoded video
        Blob renditionBlob = (Blob) transcodedVideos.get(0).get("content");
        VideoHelper.updateStoryboard(doc, renditionBlob);
        try {
            VideoHelper.updatePreviews(doc, renditionBlob);
        } catch (IOException e) {
            log.error(e);
        }

        session.saveDocument(doc);
        commitOrRollbackTransaction();
        setStatus("Done");
    }

    protected void processConversion(JSONObject object, List<Map<String, Serializable>> transcodedVideos,
                                     BlobProvider blobProvider, String filename) throws JSONException, IOException {

        String digest = object.getString("etag");
        long length = object.getLong("length");
        String conversionName =  object.getString("name");
        String extension = object.getString("extension");
        Long width = object.getLong("width");
        Long height = object.getLong("height");

        MimetypeRegistry mimetypeRegistry = Framework.getService(MimetypeRegistry.class);

        BlobInfo blobInfo = new BlobInfo();
        blobInfo.key = digest;
        blobInfo.mimeType = mimetypeRegistry.getMimetypeFromExtension(extension);
        blobInfo.filename = FilenameUtils.getBaseName(filename)+"_"+height+"p."+extension;
        blobInfo.length = length;
        blobInfo.digest = digest;

        Blob conversion = blobProvider.readBlob(blobInfo);

        Map<String, Serializable> infoMap = new HashMap<>();
        infoMap.put("width",width);
        infoMap.put("height",height);
        VideoInfo info = VideoInfo.fromMap(infoMap);
        TranscodedVideo transcodedVideo = TranscodedVideo.fromBlobAndInfo(conversionName, conversion, info);
        transcodedVideos.add(transcodedVideo.toMap());
    }

}
