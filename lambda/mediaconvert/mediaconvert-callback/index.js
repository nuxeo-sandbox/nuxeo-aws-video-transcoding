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


/*jshint esversion: 6 */

const AWS = require('aws-sdk');
const url = require('url');
const request = require('request');
const s3 = new AWS.S3();


// Get MediaConvert Output S3 object
function getMediaConvertOutputObject(metadata) {
    return new Promise(function (resolve, reject) {
        const params = {
            Key: metadata.key,
            Bucket: metadata.bucket,
            Range: "bytes=0-1" // we don't want to get entire object, we persuing ETag meta here
        };
        s3.getObject(params, function (err,data) {
            console.log('Getting Object for ', params,err);
            if (err) {
                reject('Error:', err);
            } else {
                console.log('Object', data);
                metadata.length = parseInt(data.ContentRange.split('/')[1]);
                metadata.etag = data.ETag.replace(/^"|"$/g, '');
            resolve(metadata);
            }
        });
    });
}

// copy (rename) output objects with their MD5 digest
function copyMediaConvertOutputObjects(arr) {
    var promises = [];
    for (const metadata of arr) {
        const params = {
          CopySource: metadata.bucket + '/' + metadata.key,
          Key: metadata.etag,
          Bucket: metadata.bucket
        };
        const pr = new Promise(function(resolve, reject) {
            // first copy to get an MD5 etag
            s3.copyObject(params, function (err,data) {
                console.log('First Copy for params', params);
                if (err) {
                    reject(err);
                } else {
                    console.log('First Copy', data);
                    //Second copy to set the MD5 etag as the key
                    params.CopySource = metadata.bucket + '/' + params.Key;
                    params.Key = data.CopyObjectResult.ETag.replace(/^"|"$/g, '');

                    metadata.toDelete = [];
                    metadata.toDelete.push({
                      Key:metadata.key
                    });

                    s3.copyObject(params, function (err,data) {
                        console.log('Second copy for params', params);
                        if (err) {
                            reject(err);
                        } else {
                            console.log('Second Copy', data);
                            metadata.toDelete.push({
                              Key:metadata.etag
                            });

                            metadata.key = data.CopyObjectResult.ETag.replace(/^"|"$/g, '');
                            metadata.etag = data.CopyObjectResult.ETag.replace(/^"|"$/g, '');

                            resolve(metadata);
                        }
                    });
                }
            });
        });
        promises.push(pr);
    }
    return Promise.all(promises);
}

// Delete MediaConvert Output S3 object
function deleteMediaConvertOutputObjects(arr) {
    var objects = [];
    var bucket = arr[0].bucket;
    for (const metadata of arr) {
      for (const toDelete of metadata.toDelete) {
        objects.push(toDelete);
      }
    }

    return new Promise(function (resolve, reject) {
      const params = {
        Delete : {
          Objects : objects
        },
        Bucket : bucket
      };
      s3.deleteObjects(params, function(err,data) {
          console.log('Deleting objects', JSON.stringify(params));
          if (err) {
              reject(err);
          } else {
              resolve(arr);
          }
      });
  });
}


function buildPath(host, cb, success) {
    const domain = host + '/site/lambda/';
    if (success) {
        return domain + 'success/' +  cb;
    } else {
        return domain + 'error/' + cb;
    }
}


function callREST(meta, host, cb, success=true, error=null) {
    var json = {};
    json.conversions = meta;
    json.error = error;

    console.log('JSON: ', json);
    const path = buildPath(host, cb, success);
    console.log("request: ", path);
    return new Promise(function (resolve, reject) {
        request.post(path, {
            json: json,
            headers: {
                'Content-Type': 'application/json'
            }
        }, function (err, response, _) {
            if (err) {
                console.log('Error on callREST', err);
                reject(err);
                return;
            }
            if (success) {
                resolve(response);
            } else {
                resolve(error);
            }
        });
    });
}


function processOutput(job,metadata,callback) {
    var promises = [];
    var destinationUrl = url.parse(job.Settings.OutputGroups[0].OutputGroupSettings.FileGroupSettings.Destination);
    var bucket = destinationUrl.host;
    var path = destinationUrl.pathname.replace(/^\/+|\/+$/g, '');
    var inputUrl =  url.parse(job.Settings.Inputs[0].FileInput);
    var inputKey = inputUrl.pathname.replace(/^\/+|\/+$/g, '').replace(/\.[^/.]+$/, "");

    var OutputDetails = job.OutputGroupDetails[0].OutputDetails;
    var OutputConfigs = job.Settings.OutputGroups[0].Outputs;

    console.log('OutputConfigs: ',JSON.stringify(OutputConfigs));
    console.log('OutputDetails: ',JSON.stringify(OutputDetails));

    var outputs = [];

    for(var i=0;i<OutputDetails.length;i++) {
        var output = {
            bucket : bucket,
            key: path+'/'+inputKey+OutputConfigs[i].NameModifier+'.'+OutputConfigs[i].Extension,
            width: OutputDetails[i].VideoDetails.WidthInPx,
            height: OutputDetails[i].VideoDetails.HeightInPx,
            name: OutputConfigs[i].ContainerSettings.Container +' '+OutputDetails[i].VideoDetails.HeightInPx+'p',
            extension:OutputConfigs[i].Extension
        };
        outputs.push(output);
    }

    outputs.forEach(function(output) {
      console.log('Output: ',JSON.stringify(output));
      promises.push(getMediaConvertOutputObject(output));
    });

    Promise.all(promises)
        .then(function (results) {
            console.log('Get Result', JSON.stringify(results));
            return copyMediaConvertOutputObjects(results);
        })
        .then(function (results) {
            console.log('Copy Result', JSON.stringify(results));
            return deleteMediaConvertOutputObjects(results);
        })
        .then(function (results) {
            console.log('Result', JSON.stringify(results));
            return callREST(results, metadata.host, metadata.cbId);
        })
        .then(function (resp) {
            console.log('Response',JSON.stringify(resp));
            callback(null,resp);
        })
        .catch(function (err) {
            console.log(err);
            callback(err);
        });
}


exports.handler = (event, context, callback) => {

    console.log('event:', JSON.stringify(event));

    const message = JSON.parse(event.Records[0].Sns.Message);

    if (message['detail-type'] !== "MediaConvert Job State Change") {
        //do nothing
        callback(null);
    }

    const detail = message.detail;

    const userMetadata = detail.userMetadata;

    if (detail.status === "ERROR") {
      //handle error
      callREST(detail, userMetadata.host, userMetadata.cbId,false).then(function(resp) {
        callback(null);
      });
      return;
    }

    const mediaconvert = new AWS.MediaConvert({
        apiVersion: '2017-08-29',
        endpoint: process.env.mediaConvertEndpoint
    });

    mediaconvert.getJob({Id:detail.jobId}, function(err, data) {
        if (err) {
            console.log('Error fetching job',JSON.stringify(err));
            callback(err);
        } else {
           console.log('Job',JSON.stringify(data));
           processOutput(data.Job,userMetadata,callback);
        }
    });

}
