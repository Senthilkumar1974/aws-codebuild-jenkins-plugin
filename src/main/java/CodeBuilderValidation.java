/*
 *  Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with the License.
 *     A copy of the License is located at
 *
 *         http://aws.amazon.com/apache2.0/
 *
 *     or in the "license" file accompanying this file.
 *     This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and limitations under the License.
 *
 *  Portions copyright Copyright 2004-2011 Oracle Corporation.
 *  Please see LICENSE.txt for applicable license terms and NOTICE.txt for applicable notices.
 */
import com.amazonaws.services.codebuild.model.*;
import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.BucketVersioningConfiguration;
import hudson.FilePath;

import java.util.Collection;

import static com.amazonaws.codebuild.jenkinsplugin.Validation.*;
import static enums.SourceControlType.JenkinsSource;
import static enums.SourceControlType.ProjectSource;

public class CodeBuilderValidation {

    public static final String invalidArtifactTypeError = "Artifact type override must be one of 'NO_ARTIFACTS', 'S3', ''";
    public static final String invalidArtifactsPackagingError = "Artifact packaging override must be one of 'NONE', 'ZIP', ''";
    public static final String invalidArtifactNamespaceTypeError = "Artifact namespace override must be one of 'NONE', 'BUILD_ID', ''";
    public static final String invalidTimeoutOverrideError = "Build timeout override must be a number between 5 and 480 (minutes)";
    public static final String invalidRegionError = "Enter a valid AWS region";
    public static final String invalidProxyError = "Enter a valid proxy host and port (greater than zero)";
    public static final String invalidCredentialsIdError = "Invalid credentials ID. Verify that the credentials are of type CodeBuildCredentials and are accessible in this project.";
    public static final String invalidSourceTypeError = "Source type override must be one of 'CODECOMMIT', 'S3', 'GITHUB', 'GITHUB_ENTERPRISE', 'BITBUCKET'";
    public static final String invalidComputeTypeError = "Compute type override must be one of 'BUILD_GENERAL1_SMALL', 'BUILD_GENERAL1_MEDIUM', 'BUILD_GENERAL1_LARGE'";
    public static final String invalidEnvironmentTypeError = "Environment type override must be one of 'LINUX_CONTAINER', 'WINDOWS_CONTAINER'";
    public static final String invalidCacheTypeError = "Cache type override must be one of 'S3', 'NO_CACHE', 'LOCAL'";
    public static final String invalidCacheModesError = "Cache modes override must be one or more of 'LOCAL_SOURCE_CACHE', 'LOCAL_DOCKER_LAYER_CACHE', 'LOCAL_CUSTOM_CACHE' and enclosed in brackets";
    public static final String invalidCloudWatchLogsStatusError = "CloudWatch Logs status override must be one of 'ENABLED', 'DISABLED'";
    public static final String invalidS3LogsStatusError = "S3 logs status override must be one of 'ENABLED', 'DISABLED'";
    public static final String invalidSourceUploaderNullWorkspaceError = "Project workspace is null";
    public static final String invalidSourceUploaderNullS3ClientError = "S3 client cannot be null";
    public static final String invalidSourceUploaderConfigError = "Cannot specify both localSourcePath and workspaceSubdir";
    public static final String projectRequiredError = "CodeBuild project name is required";
    public static final String sourceControlTypeRequiredError = "Source control type is required and must be 'jenkins' or 'project'";
    public static final String buildInstanceRequiredError = "Build instance is required";

    //// Configuration-checking functions ////
    // CodeBuilder: if any of the parameters in CodeBuilder are bad, this will cause the build to end in failure in CodeBuilder.perform()
    public static String checkEssentialConfig(CodeBuilder cb) {
        String projectName = cb.getParameterized(cb.getProjectName());
        if(projectName == null || projectName.isEmpty()) {
            return projectRequiredError;
        }
        String sourceControlType = cb.getParameterized(cb.getSourceControlType());
        if(!sourceControlType.equals(JenkinsSource.toString()) &&
            !sourceControlType.equals(ProjectSource.toString())) {
            return sourceControlTypeRequiredError;
        }
        return "";
    }

    // Returns empty string if configuration valid
    public static String checkStartBuildOverridesConfig(CodeBuilder cb) {
        String artifactTypeOverride = cb.getParameterized(cb.getArtifactTypeOverride());
        if(!artifactTypeOverride.isEmpty()) {
            try {
                ArtifactsType.fromValue(artifactTypeOverride);
            } catch(IllegalArgumentException e) {
                return invalidArtifactTypeError;
            }
        }
        String artifactPackagingOverride = cb.getParameterized(cb.getArtifactPackagingOverride());
        if(!artifactPackagingOverride.isEmpty()) {
            try {
                ArtifactPackaging.fromValue(artifactPackagingOverride);
            } catch(IllegalArgumentException e) {
                return invalidArtifactsPackagingError;
            }
        }

        String artifactNamespaceOverride = cb.getParameterized(cb.getArtifactNamespaceOverride());
        if(!artifactNamespaceOverride.isEmpty()) {
            try {
                ArtifactNamespace.fromValue(artifactNamespaceOverride);
            } catch(IllegalArgumentException e) {
                return invalidArtifactNamespaceTypeError;
            }
        }

        String sourceTypeOverride = cb.getParameterized(cb.getSourceTypeOverride());
        if(!sourceTypeOverride.isEmpty()) {
            try {
                SourceType.fromValue(sourceTypeOverride);
            } catch(IllegalArgumentException e) {
                return invalidSourceTypeError;
            }
        }

        String computeTypeOverride = cb.getParameterized(cb.getComputeTypeOverride());
        if(!computeTypeOverride.isEmpty()) {
            try {
                ComputeType.fromValue(computeTypeOverride);
            } catch(IllegalArgumentException e) {
                return invalidComputeTypeError;
            }
        }

        String environmentTypeOverride = cb.getParameterized(cb.getEnvironmentTypeOverride());
        if(!environmentTypeOverride.isEmpty()) {
            try {
                EnvironmentType.fromValue(environmentTypeOverride);
            } catch(IllegalArgumentException e) {
                return invalidEnvironmentTypeError;
            }
        }

        String cacheTypeOverride = cb.getParameterized(cb.getCacheTypeOverride());
        if(!cacheTypeOverride.isEmpty()) {
            try {
                CacheType.fromValue(cacheTypeOverride);
            } catch(IllegalArgumentException e) {
                return invalidCacheTypeError;
            }
        }

        String cacheModesOverride = cb.getParameterized(cb.getCacheModesOverride());
        if (!cacheModesOverride.isEmpty()) {
            try {
                for (String mode : cb.listCacheModes(cacheModesOverride)) {
                    CacheMode.fromValue(mode);
                }
            } catch (IllegalArgumentException e) {
                return invalidCacheModesError;
            }
        }

        String cloudWatchLogsStatusOverride = cb.getParameterized(cb.getCloudWatchLogsStatusOverride());
        if(!cloudWatchLogsStatusOverride.isEmpty()) {
            try {
                LogsConfigStatusType.fromValue(cloudWatchLogsStatusOverride);
            } catch(IllegalArgumentException e) {
                return invalidCloudWatchLogsStatusError;
            }
        }

        String s3LogsStatusOverride = cb.getParameterized(cb.getS3LogsStatusOverride());
        if(!s3LogsStatusOverride.isEmpty()) {
            try {
                LogsConfigStatusType.fromValue(s3LogsStatusOverride);
            } catch(IllegalArgumentException e) {
                return invalidS3LogsStatusError;
            }
        }

        String timeout = cb.getParameterized(cb.getBuildTimeoutOverride());
        if(timeout != null && !timeout.isEmpty()) {
            Integer t;
            try {
                t = Integer.parseInt(timeout);
            } catch(NumberFormatException e) {
                return invalidTimeoutOverrideError;
            }
            if(t < 5 || t > 480) {
                return invalidTimeoutOverrideError;
            }
        }

        return "";
    }

    public static boolean envVariablesHaveRestrictedPrefix(Collection<EnvironmentVariable> envVariables) {
        for(EnvironmentVariable e: envVariables) {
            if(e.getName().startsWith("CODEBUILD_")) {
                return true;
            }
        }
        return false;
    }

    //CloudWatchMonitor
    public static boolean checkCloudWatchMonitorConfig(AWSLogsClient client) {
        if(client == null) {
            return false;
        }
        return true;
    }

    //S3DataManager
    public static void checkS3SourceUploaderConfig(FilePath workspace, AmazonS3Client s3Client, String localSourcePath, String workspaceSubdir) throws InvalidInputException {
        if(workspace == null) {
            throw new InvalidInputException(invalidSourceUploaderNullWorkspaceError);
        }

        if(s3Client == null) {
            throw new InvalidInputException(invalidSourceUploaderNullS3ClientError);
        }

        if((localSourcePath != null && !localSourcePath.isEmpty()) && (workspaceSubdir != null && !workspaceSubdir.isEmpty())) {
            throw new InvalidInputException(invalidSourceUploaderConfigError);
        }
    }

    public static boolean checkSourceTypeS3(String sourceType) {
        if(sourceType != null && sourceType.equals("S3")) {
            return true;
        }
        return false;
    }

    public static boolean checkJenkinsSourceOverrides(String sourceTypeOverride, String sourceLocationOverride) {
        if(sourceTypeOverride.isEmpty() != sourceLocationOverride.isEmpty()) {
            return false;
        }

        return sourceTypeOverride.equals("S3");
    }


    public static boolean checkBucketIsVersioned(String bucketName, AWSClientFactory awsClientFactory) {
        final BucketVersioningConfiguration bucketVersioningConfig = awsClientFactory.getS3Client().getBucketVersioningConfiguration(bucketName);
        return bucketVersioningConfig.getStatus().equals(BucketVersioningConfiguration.ENABLED);
    }

    //AWSClientFactory
    public static void checkAWSClientFactoryJenkinsCredentialsConfig(String credentialsId) throws InvalidInputException {
        if(credentialsId == null || credentialsId.isEmpty()) {
            throw new InvalidInputException(invalidCredentialsIdError);
        }
    }

    public static void checkAWSClientFactoryRegionConfig(String region) throws InvalidInputException {
        if (region.isEmpty()) {
            throw new InvalidInputException(invalidRegionError);
        }
    }
}
