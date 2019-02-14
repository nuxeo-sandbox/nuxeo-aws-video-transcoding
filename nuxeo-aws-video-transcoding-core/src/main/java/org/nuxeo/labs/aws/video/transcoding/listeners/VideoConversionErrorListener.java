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
 *     michael vachette
 */

package org.nuxeo.labs.aws.video.transcoding.listeners;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONObject;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.PostCommitEventListener;

import java.io.Serializable;
import java.util.Map;

import static org.nuxeo.lambda.core.LambdaService.LAMBDA_RESPONSE_KEY;
import static org.nuxeo.lambda.core.LambdaService.PARAMETERS_KEY;

public class VideoConversionErrorListener implements PostCommitEventListener {

    private static final Log log = LogFactory.getLog(VideoConversionSuccessListener.class);

    @Override
    public void handleEvent(EventBundle eventBundle) {
        for (Event event : eventBundle) {
            if ("lambdaVideoFailed".equals(event.getName())) {
                handleEvent(event);
            }
        }
    }

    private void handleEvent(Event event) {
        EventContext ctx = event.getContext();
        if (ctx != null && ctx.getProperty(PARAMETERS_KEY) != null) {
            Map<String, Serializable> params = (Map<String, Serializable>) ctx.getProperty(PARAMETERS_KEY);
            final String docId = (String) params.get("docId");
            JSONObject object = (JSONObject) ctx.getProperty(LAMBDA_RESPONSE_KEY);
            try {
                JSONObject error = object.getJSONObject("error");
                log.warn("AWS Video Conversion Failed for "+docId+" :"+error);
            } catch (JSONException e) {
                throw new NuxeoException(e);
            }
        } else {
            throw new NuxeoException("Lambda callback error with a message");
        }
    }
}