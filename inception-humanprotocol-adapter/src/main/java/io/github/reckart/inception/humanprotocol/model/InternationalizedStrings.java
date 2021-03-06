/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *         http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package io.github.reckart.inception.humanprotocol.model;

import java.util.LinkedHashMap;

public class InternationalizedStrings extends LinkedHashMap<String, String>
{
    private static final long serialVersionUID = -4016926381508621715L;

    public InternationalizedStrings withString(String aLanguage, String aString) {
        put(aLanguage, aString);
        return this;
    }
}
