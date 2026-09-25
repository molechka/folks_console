package ru.olgak.folks.app;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.io.InputStream;

@Path("/")
public class FolksUiResource {

    @GET
    @Produces("text/html;charset=UTF-8")
    public Response index() {
        InputStream page = FolksUiResource.class.getResourceAsStream("/ui/index.html");
        if (page == null) {
            return Response.serverError().entity("ui/index.html not found").build();
        }
        return Response.ok(page).type("text/html;charset=UTF-8").build();
    }
}
