package osgi6.admin.exec

import java.io.{OutputStream, PrintWriter}
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import org.osgi.framework.{Bundle, BundleContext}
import osgi6.actor.ActorSystemActivator
import osgi6.akka.slf4j.AkkaSlf4j
import osgi6.common.{AsyncActivator, HttpTools, OsgiAdmin, OsgiTools}
import osgi6.lib.multi.{ContextApiActivator, MultiApiActivator}
import osgi6.multi.api.MultiApi.Callback
import osgi6.multi.api.{Context, ContextApi, MultiApi}

import scala.concurrent.Future
import scala.util.control.NonFatal

/**
  * Created by martonpapp on 10/07/16.
  */
import osgi6.admin.exec.AdminExecActivator._

class AdminExecActivator extends ActorSystemActivator(
  { input =>
    import input.actorSystem.dispatcher

    ContextApiActivator.activate(
      ContextApi.registry,
      { hasCtx =>
        hasCtx.apiContext.map({ c =>
          MultiApiActivator.activate(
            MultiApi.registry,
            activate(
              input.bundleContext,
              c
            )
          )
        }).getOrElse(AsyncActivator.Noop)
      }
    )
  },
  classLoader = Some(classOf[AdminExecActivator].getClassLoader),
  config = AkkaSlf4j.config
)

object AdminExecActivator {

  def activate(
    ctx: BundleContext,
    context: Context
  ) = {

    val handler = new MultiApi.Handler {
      override def dispatch(req: HttpServletRequest, resp: HttpServletResponse, callback: Callback): Unit = {


        val rootPath = context.rootPath
        val requestUri = req.getServletPath + Option(req.getPathInfo).getOrElse("")

        val (root, info) = requestUri.splitAt(rootPath.length)

        val servletPath = Option(info)

        if (root != rootPath) {
          callback.handled(false)
        } else {

          servletPath match {
            case Some("/_admin/exec") =>
              OsgiAdmin.dispatch(ctx, req, resp)
              callback.handled(true)

            case _ =>
              callback.handled(false)
          }

        }

      }
    }

    (
      handler,
      () => {
        Future.successful(())
      }
    )


  }


}
