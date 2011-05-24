package com.gitblit;

import java.util.Date;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants.AccessRestrictionType;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.wicket.models.RepositoryModel;

public class DownloadZipServlet extends HttpServlet {

	public static String asLink(String baseURL, String repository, String objectId, String path) {
		return baseURL + (baseURL.endsWith("/") ? "" : "/") + "zip?r=" + repository + (path == null ? "" : ("&p=" + path)) + (objectId == null ? "" : ("&h=" + objectId));
	}

	private static final long serialVersionUID = 1L;

	private final static Logger logger = LoggerFactory.getLogger(DownloadZipServlet.class);

	public DownloadZipServlet() {
		super();
	}

	@Override
	protected void doPost(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response) throws javax.servlet.ServletException, java.io.IOException {
		processRequest(request, response);
	}

	@Override
	protected void doGet(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response) throws javax.servlet.ServletException, java.io.IOException {
		processRequest(request, response);
	}

	private void processRequest(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response) throws javax.servlet.ServletException, java.io.IOException {
		if (!GitBlit.self().settings().getBoolean(Keys.web.allowZipDownloads, true)) {
			logger.warn("Zip downloads are disabled");
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;

		}
		String repository = request.getParameter("r");
		String basePath = request.getParameter("p");
		String objectId = request.getParameter("h");

		try {
			String name = repository;
			if (name.indexOf('/') > -1) {
				name = name.substring(name.lastIndexOf('/') + 1);
			}

			// check roles first
			boolean authorized = request.isUserInRole(Constants.ADMIN_ROLE);
			authorized |= request.isUserInRole(repository);

			if (!authorized) {
				RepositoryModel model = GitBlit.self().getRepositoryModel(repository);
				if (model.accessRestriction.atLeast(AccessRestrictionType.VIEW)) {
					logger.warn("Unauthorized access via zip servlet for " + model.name);
					response.sendError(HttpServletResponse.SC_FORBIDDEN);
					return;
				}
			}
			if (!StringUtils.isEmpty(basePath)) {
				name += "-" + basePath.replace('/', '_');
			}
			if (!StringUtils.isEmpty(objectId)) {
				name += "-" + objectId;
			}

			Repository r = GitBlit.self().getRepository(repository);
			RevCommit commit = JGitUtils.getCommit(r, objectId);
			Date date = JGitUtils.getCommitDate(commit);
			String contentType = "application/octet-stream";
			response.setContentType(contentType + "; charset=" + response.getCharacterEncoding());
			// response.setContentLength(attachment.getFileSize());
			response.setHeader("Content-Disposition", "attachment; filename=\"" + name + ".zip" + "\"");
			response.setDateHeader("Last-Modified", date.getTime());
			response.setHeader("Cache-Control", "no-cache");
			response.setHeader("Pragma", "no-cache");
			response.setDateHeader("Expires", 0);

			try {
				JGitUtils.zip(r, basePath, objectId, response.getOutputStream());
				response.flushBuffer();
			} catch (Throwable t) {
				logger.error("Failed to write attachment to client", t);
			}
		} catch (Throwable t) {
			logger.error("Failed to write attachment to client", t);
		}
	}
}