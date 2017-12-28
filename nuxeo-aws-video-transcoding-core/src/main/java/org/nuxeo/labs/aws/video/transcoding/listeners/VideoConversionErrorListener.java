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
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.PostCommitEventListener;

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
        //todo
        throw new NuxeoException("error");
    }
}