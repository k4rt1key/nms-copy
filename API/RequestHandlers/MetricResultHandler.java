package org.nms.API.RequestHandlers;

import io.vertx.ext.web.RoutingContext;
import org.nms.App;
import org.nms.API.Utility.HttpResponse;

public class MetricResultHandler
{
    public static void getAllPolledData(RoutingContext ctx)
    {
        App.metricResultModel
                .getAll()
                .onSuccess((polledData -> HttpResponse.sendSuccess(ctx, 200,"Polled Data", polledData)))
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong"));
    }
}
