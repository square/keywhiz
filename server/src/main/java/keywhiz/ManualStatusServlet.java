/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package keywhiz;

import java.io.IOException;
import java.io.Serializable;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** A Servlet added to the Admin Connector for manually making _status report unhealthy */
public class ManualStatusServlet extends HttpServlet implements Serializable {
  private final ManualStatusHealthCheck mshc;

  public ManualStatusServlet(ManualStatusHealthCheck mshc) {
    this.mshc = mshc;
  }

  @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    String path = req.getPathInfo();
    if(path != null && path.equals("/enable")){
      mshc.setHealthy(true);
    } else if(path != null && path.equals("/disable")) {
      mshc.setHealthy(false);
    } else {
      resp.sendError(HttpServletResponse.SC_NOT_FOUND,
          "Need to pass /status/enable or /status/disable, not /status" + path);
    }
  }

}
