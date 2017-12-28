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

exports.handler = (event, context, callback) => {
  console.log('event:', JSON.stringify(event));

  const host = event.host;
  const bucket = event.input.bucket;
  const digest = event.input.key;
  const height = event.input.videoInfo.height;
  const inputStreams = event.input.videoInfo.streams;

  //is there an audio stream?
  var hasAudioStream = false;
  inputStreams.forEach(function(stream) {
    if (stream.type === "Audio") {
      hasAudioStream = true;
    }
  });

  var params = {
    Role: process.env.mediaConvertRole,
    Settings: {
      Inputs: [{
        FileInput: 's3://' + bucket + '/' + digest
      }],
      OutputGroups: [{
        Name: 'MP4',
        OutputGroupSettings: {
          Type: 'FILE_GROUP_SETTINGS',
          FileGroupSettings: {
            Destination: 's3://' + bucket + '/' + digest + '/mediaconvert_mp4/'
          }
        },
        Outputs: [{
          Extension: 'mp4',
          NameModifier: '_H264_1280p',
          VideoDescription: {
            ScalingBehavior: "DEFAULT",
            Height: 720,
            TimecodeInsertion: "DISABLED",
            AntiAlias: "ENABLED",
            Sharpness: 50,
            CodecSettings: {
              Codec: "H_264",
              H264Settings: {
                InterlaceMode: "PROGRESSIVE",
                NumberReferenceFrames: 3,
                Syntax: "DEFAULT",
                Softness: 0,
                GopClosedCadence: 1,
                GopSize: 90,
                Slices: 1,
                GopBReference: "DISABLED",
                SlowPal: "DISABLED",
                SpatialAdaptiveQuantization: "ENABLED",
                TemporalAdaptiveQuantization: "ENABLED",
                FlickerAdaptiveQuantization: "DISABLED",
                EntropyEncoding: "CABAC",
                Bitrate: 5000000,
                FramerateControl: "INITIALIZE_FROM_SOURCE",
                RateControlMode: "VBR",
                CodecProfile: "MAIN",
                Telecine: "NONE",
                MinIInterval: 0,
                AdaptiveQuantization: "HIGH",
                CodecLevel: "AUTO",
                FieldEncoding: "PAFF",
                SceneChangeDetect: "ENABLED",
                QualityTuningLevel: "SINGLE_PASS",
                FramerateConversionAlgorithm: "DUPLICATE_DROP",
                UnregisteredSeiTimecode: "DISABLED",
                GopSizeUnits: "FRAMES",
                ParControl: "INITIALIZE_FROM_SOURCE",
                NumberBFramesBetweenReferenceFrames: 2,
                RepeatPps: "DISABLED"
              }
            },
            AfdSignaling: "NONE",
            DropFrameTimecode: "ENABLED",
            RespondToAfd: "NONE",
            ColorMetadata: "INSERT"
          },
          ContainerSettings: {
            Container: "MP4",
            Mp4Settings: {
              CslgAtom: "INCLUDE",
              FreeSpaceBox: "EXCLUDE",
              MoovPlacement: "PROGRESSIVE_DOWNLOAD"
            }
          }
        }]
      }]
    },
    UserMetadata: {
      cbId: event.cbId,
      host: host,
      digest: digest,
      bucket: bucket
    }
  };

  if (hasAudioStream) {
    params.Settings.Inputs[0].AudioSelectors = {
      "Audio Selector 1": {
        Tracks: [
          1
        ],
        Offset: 0,
        DefaultSelection: "NOT_DEFAULT",
        SelectorType: "TRACK",
        ProgramSelection: 1
      }
    };
    params.Settings.OutputGroups[0].Outputs[0].AudioDescriptions = [{
      AudioTypeControl: "FOLLOW_INPUT",
      AudioSourceName: "Audio Selector 1",
      CodecSettings: {
        Codec: "AAC",
        AacSettings: {
          AudioDescriptionBroadcasterMix: "NORMAL",
          Bitrate: 96000,
          RateControlMode: "CBR",
          CodecProfile: "LC",
          CodingMode: "CODING_MODE_2_0",
          RawFormat: "NONE",
          SampleRate: 48000,
          Specification: "MPEG4"
        }
      },
      LanguageCodeControl: "FOLLOW_INPUT"
    }];
  }

  var mediaconvert = new AWS.MediaConvert({
    apiVersion: '2017-08-29',
    endpoint: process.env.mediaConvertEndpoint
  });

  mediaconvert.createJob(params, function(err, data) {
    if (err) {
      console.log(err, err.stack);
    } else {
      console.log('Job', JSON.stringify(data));
    }
  });
};
