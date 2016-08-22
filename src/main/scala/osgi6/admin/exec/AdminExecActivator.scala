package osgi6.admin.exec

import java.io.{OutputStream, PrintWriter}
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import org.osgi.framework.{Bundle, BundleContext}
import osgi6.actor.ActorSystemActivator
import osgi6.akka.slf4j.AkkaSlf4j
import osgi6.common.{AsyncActivator, HttpTools, OsgiTools}
import osgi6.lib.multi.{ContextApiActivator, MultiApiActivator}
import osgi6.multi.api.MultiApi.Callback
import osgi6.multi.api.{Context, MultiApi}

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
      { hasCtx =>
        hasCtx.apiContext.map({ c =>
          MultiApiActivator.activate(
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
        def processAdminRequestHtml(fn: => String) =
          processAdminRequest(fn, "text/html")

        def processAdminRequest(fn: => String, ct: String = "text/plain") = {
          processAdminRequestStream({ os =>
            os.write(fn.getBytes)
          })
        }

        def processAdminRequestStream(fn: OutputStream => Unit, ct: String = "text/plain") = {
          resp.setContentType(ct)
          HttpTools.preResponse(req, resp)
          val os = resp.getOutputStream
          try {
            try {
              fn(os)
            } catch {
              case NonFatal(ex) =>
                val pw = new PrintWriter(os)
                ex.printStackTrace(pw)
                pw.close()
            }
          } finally {
            os.close()
          }
          callback.handled(true)
        }

        val rootPath = context.rootPath
        val requestUri = req.getServletPath + Option(req.getPathInfo).getOrElse("")

        val (root, info) = requestUri.splitAt(rootPath.length)

        val servletPath = Option(info)

        if (root != rootPath) {
          callback.handled(false)
        } else {

          servletPath match {
            case Some("/_admin/exec") =>
              processAdminRequestStream { os =>
                OsgiTools.execBundle(
                  ctx,
                  req.getInputStream,
                  os
                )
              }
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


  def getStateString(bundle: Bundle) : String = {
    getStateString(bundle.getState)
  }
  def getStateString(state: Int) : String = {
    if (state == 32) "Active     "
    else if (state == 2) "Installed  "
    else if (state == 4) "Resolved   "
    else if (state == 8) "Starting   "
    else if (state == 16) "Stopping   "
    else "Unknown    "
  }
}
