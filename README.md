## Description
This plugin provides an integration between AWS Mediaconvert and the Nuxeo Platform. AWS Lambda is leveraged to queue conversion jobs and notify the Nuxeo Platform when jobs are completed.

## Important Note

**These features are not part of the Nuxeo Production platform.**

These solutions are provided for inspiration and we encourage customers to use them as code samples and learning resources.

This is a moving project (no API maintenance, no deprecation process, etc.) If any of these solutions are found to be useful for the Nuxeo Platform in general, they will be integrated directly into platform, not maintained here.

## Requirements
Building requires the following software:
- git
- maven
- npm

## How to build
```
git clone https://github.com/nuxeo-sandbox/nuxeo-aws-video-transcoding
cd nuxeo-aws-video-transcoding
mvn clean install
```

## Deploying
* Install the marketplace package from the admin center or using nuxeoctl

## AWS Configuration
This plugin shares AWS credentials with the [S3 storage package](https://connect.nuxeo.com/nuxeo/site/marketplace/package/amazon-s3-online-storage)

* Create a Role for mediaconvert [documentation](http://docs.aws.amazon.com/mediaconvert/latest/ug/iam-role.html)

* Create a lambda function named aws-video-conversions
  * Copy the content of lambda/aws-video-conversions/index.js
  * Add two environment variables in the function Configuration
    * mediaConvertEndpoint: endpoint url that can be found from the AWS console in the mediaconvert service configuration
    * mediaConvertRole: arn of the mediaconvert role created previously


* Package the callback function
```
cd lambda/mediaconvert/aws-video-conversions
npm install
```
  Zip the content of lambda/aws-video-conversions


* Create a second lambda function named mediaconvert-callback:
  * Upload the zip file created previously
  * Add an environment variable in the function onfiguration
    * mediaConvertEndpoint: mediaconvert endpoint url that can be found from the AWS console in the mediaconvert service configuration


- in IAM, add the following inline policy to your lambda Role
```
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": "mediaconvert:*",
            "Resource": "*"
        }
    ]
}
```
- Setup Cloudwatch and SNS [documentation](http://docs.aws.amazon.com/mediaconvert/latest/ug/mediaconvert_sns_tutorial.html)

## Known limitations
This plugin is a work in progress.

## About Nuxeo
Nuxeo dramatically improves how content-based applications are built, managed and deployed, making customers more agile, innovative and successful. Nuxeo provides a next generation, enterprise ready platform for building traditional and cutting-edge content oriented applications. Combining a powerful application development environment with SaaS-based tools and a modular architecture, the Nuxeo Platform and Products provide clear business value to some of the most recognizable brands including Verizon, Electronic Arts, Netflix, Sharp, FICO, the U.S. Navy, and Boeing. Nuxeo is headquartered in New York and Paris. More information is available at [www.nuxeo.com](http://www.nuxeo.com).
