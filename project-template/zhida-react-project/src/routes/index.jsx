import AboutPage from "@/pages/AboutPage"
import IndexPage from "@/pages/IndexPage"
import { createHashRouter } from "react-router"

const router = createHashRouter([
  // 在此定义路由对象
  {
    path: '/',
    Component: IndexPage
  },
  {
    path: '/about',
    Component: AboutPage
  }
])

export default router
