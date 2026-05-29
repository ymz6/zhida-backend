import { TooltipProvider } from "@/components/ui/tooltip"
import { Toaster } from "@/components/ui/sonner"
import { RouterProvider } from "react-router/dom"
import router from "./routes"

function App() {
  return (
    <>
      <TooltipProvider>
        <Toaster />
        <RouterProvider router={router} />
      </TooltipProvider>
    </>
  )
}

export default App
