// Copyright (C) 2015 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.oauth;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.scribe.builder.api.DefaultApi20;
import org.scribe.exceptions.OAuthException;
import org.scribe.extractors.AccessTokenExtractor;
import org.scribe.model.OAuthConfig;
import org.scribe.model.OAuthConstants;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.model.Verifier;
import org.scribe.utils.OAuthEncoder;
import org.scribe.oauth.OAuthService;
import org.scribe.utils.Preconditions;

public class UoMGitLab2Api extends DefaultApi20 {
    private static final String AUTHORIZE_URL =
        "https://gitlab.cs.man.ac.uk/oauth/authorize?client_id=%s&redirect_uri=%s&response_type=code";

    @Override
    public String getAccessTokenEndpoint() {
        return "https://gitlab.cs.man.ac.uk/oauth/token";
    }


    @Override
    public String getAuthorizationUrl(OAuthConfig config) {
        return String.format(AUTHORIZE_URL,
                             config.getApiKey(),
                             OAuthEncoder.encode(config.getCallback()));
    }


    @Override
    public Verb getAccessTokenVerb() {
        return Verb.POST;
    }


    @Override
    public AccessTokenExtractor getAccessTokenExtractor() {
        return new UoMGitLabJsonTokenExtractor();
    }

    private static final class UoMGitLabJsonTokenExtractor implements AccessTokenExtractor {
        private Pattern accessTokenPattern = Pattern.compile("\"access_token\"\\s*:\\s*\"(\\S*?)\"");

        @Override
        public Token extract(String response) {
            Preconditions.checkEmptyString(response,
                                           "Cannot extract a token from a null or empty String");
            Matcher matcher = accessTokenPattern.matcher(response);

            if (matcher.find()) {
                return new Token(matcher.group(1), "", response);
            } else {
                throw new OAuthException("Cannot extract an acces token. Response was: " + response);
            }
        }
    }


    @Override
    public OAuthService createService(OAuthConfig config) {
        return new UoMGitLabOAuthService(this, config);
    }

    private static final class UoMGitLabOAuthService implements OAuthService {
        private static final String VERSION = "2.0";

        private static final String GRANT_TYPE = "grant_type";
        private static final String GRANT_TYPE_VALUE = "authorization_code";

        private final DefaultApi20 api;
        private final OAuthConfig config;

        /**
         * Default constructor
         *
         * @param api OAuth2.0 api information
         * @param config OAuth 2.0 configuration param object
         */
        public UoMGitLabOAuthService(DefaultApi20 api, OAuthConfig config) {
            this.api = api;
            this.config = config;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Token getAccessToken(Token requestToken, Verifier verifier) {
            OAuthRequest request = new OAuthRequest(api.getAccessTokenVerb(),
                                                    api.getAccessTokenEndpoint());

            request.addBodyParameter(OAuthConstants.CLIENT_ID    , config.getApiKey());
            request.addBodyParameter(OAuthConstants.CLIENT_SECRET, config.getApiSecret());
            request.addBodyParameter(OAuthConstants.CODE         , verifier.getValue());
            request.addBodyParameter(OAuthConstants.REDIRECT_URI , config.getCallback());
            request.addBodyParameter(GRANT_TYPE                  , GRANT_TYPE_VALUE);

            Response response = request.send();

            return api.getAccessTokenExtractor().extract(response.getBody());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Token getRequestToken() {
            throw new UnsupportedOperationException(
                "Unsupported operation, please use 'getAuthorizationUrl' and redirect your users there");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getVersion() {
            return VERSION;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void signRequest(Token accessToken, OAuthRequest request) {
            request.addQuerystringParameter(OAuthConstants.ACCESS_TOKEN,
                                            accessToken.getToken());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getAuthorizationUrl(Token requestToken) {
            return api.getAuthorizationUrl(config);
        }
    }
}
