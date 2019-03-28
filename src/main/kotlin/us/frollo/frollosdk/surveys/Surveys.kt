/*
 * Copyright 2019 Frollo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package us.frollo.frollosdk.surveys

import us.frollo.frollosdk.base.Resource
import us.frollo.frollosdk.core.OnFrolloSDKCompletionListener
import us.frollo.frollosdk.extensions.enqueue
import us.frollo.frollosdk.logging.Log
import us.frollo.frollosdk.model.coredata.surveys.Survey
import us.frollo.frollosdk.network.NetworkService
import us.frollo.frollosdk.network.api.SurveysAPI

class Surveys(network: NetworkService) {

    companion object {
        private const val TAG = "Surveys"
    }

    private val surveysAPI: SurveysAPI = network.create(SurveysAPI::class.java)

    fun fetchSurvey(surveyKey: String, completion: OnFrolloSDKCompletionListener<Resource<Survey>>? = null) {
        surveysAPI.fetchSurvey(surveyKey = surveyKey).enqueue { resource ->
            if (resource.status == Resource.Status.ERROR) {
                Log.e("$TAG#fetchSurvey", resource.error?.localizedDescription)
            }
            completion?.invoke(resource)
        }
    }

    fun submitSurvey(survey: Survey, completion: OnFrolloSDKCompletionListener<Resource<Survey>>? = null) {
        surveysAPI.submitSurvey(request = survey).enqueue { resource ->
            if (resource.status == Resource.Status.ERROR) {
                Log.e("$TAG#submitSurvey", resource.error?.localizedDescription)
            }
            completion?.invoke(resource)
        }
    }
}