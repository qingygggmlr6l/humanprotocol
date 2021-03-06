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
package io.github.reckart.inception.humanprotocol.security;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.apache.commons.io.IOUtils;

public class BufferingHttpServletRequestWrapper
    extends HttpServletRequestWrapper
{
    private byte[] byteBuffer;
    private String stringBuffer;

    public BufferingHttpServletRequestWrapper(HttpServletRequest aRequest)
    {
        super(aRequest);
    }

    @Override
    public BufferedReader getReader() throws IOException
    {
        if (stringBuffer == null) {
            stringBuffer = IOUtils.toString(super.getReader());
        }
        
        return new BufferedReader(new StringReader(stringBuffer));
    }
    
    @Override
    public ServletInputStream getInputStream() throws IOException
    {
        if (byteBuffer == null) {
            byteBuffer = IOUtils.toByteArray(super.getInputStream());
        }
        
        return new ByteArrayServletInputStream(byteBuffer);
    }
}
