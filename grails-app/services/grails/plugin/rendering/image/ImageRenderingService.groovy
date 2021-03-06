/*
 * Copyright 2010 Grails Plugin Collective
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.plugin.rendering.image

import org.w3c.dom.Document

import java.awt.*
import java.awt.image.*
import java.awt.geom.AffineTransform
import javax.imageio.ImageIO

import org.xhtmlrenderer.simple.Graphics2DRenderer

import grails.plugin.rendering.RenderingService
import grails.plugin.rendering.datauri.DataUriAwareNaiveUserAgent

abstract class ImageRenderingService extends RenderingService {

	static transactional = false
	
	static DEFAULT_BUFFERED_IMAGE_TYPE = BufferedImage.TYPE_INT_ARGB
	
	abstract protected getImageType()
	
	protected configureRenderer(Graphics2DRenderer renderer) {
		renderer.sharedContext.userAgentCallback = new DataUriAwareNaiveUserAgent()
	}
	
	protected doRender(Map args, Document document, OutputStream outputStream) {
		convert(args, createBufferedImage(args, document), outputStream)
	}
	
	protected convert(Map args, BufferedImage image, OutputStream outputStream) {
		def imageType = getImageType()
		if (!ImageIO.write(image, imageType, outputStream)) {
			throw new IllegalArgumentException("ImageIO.write() failed to find writer for type '$type'")
		}
	}
	
	protected getDefaultBufferedImageType() {
		DEFAULT_BUFFERED_IMAGE_TYPE
	}
	
	protected BufferedImage createBufferedImage(Map args, Document document) {
		def bufferedImageType = args.bufferedImageType ?: getDefaultBufferedImageType()
		
		def renderWidth = args.render?.width?.toInteger() ?: 10
		def renderHeight = args.render?.height?.toInteger()
		
		def autosizeWidth = args.autosize?.width == null || args.autosize?.width == true
		def autosizeHeight = args.autosize?.height == null || args.autosize?.height == true
		
		def renderer = new Graphics2DRenderer()
		configureRenderer(renderer)
		renderer.setDocument(document, args.base)
		
		def imageWidth = renderWidth
		def imageHeight = renderHeight
		def needsLayout = true
		
		if (!renderHeight || autosizeWidth || autosizeHeight) {
			def tempRenderHeight = renderHeight ?: 10000
			def dim = new Dimension(renderWidth, tempRenderHeight)
			
			// do layout with temp buffer to calculate height
			def tempImage = new BufferedImage(dim.width.intValue(), dim.height.intValue(), bufferedImageType)
			def tempGraphics = tempImage.graphics
			renderer.layout(tempGraphics, dim)
			needsLayout = false
			tempGraphics.dispose()
			
			if (autosizeWidth) {
				imageWidth = renderer.minimumSize.width.intValue()
			} 
			if (!renderHeight || autosizeHeight) {
				imageHeight = renderer.minimumSize.height.intValue()
			}
		}

		def image = new BufferedImage(imageWidth, imageHeight, bufferedImageType)
		def graphics = image.graphics
		if (needsLayout) {
			renderer.layout(graphics, new Dimension(imageWidth, imageHeight))
		}
		renderer.render(graphics)
		graphics.dispose()
		
		if (args.scale) {
			scale(image, args.scale, bufferedImageType)
		} else if (args.resize) {
			resize(image, args.resize, bufferedImageType)
		} else {
			image
		}
	}
	
	protected scale(image, Map scaleArgs, bufferedImageType) {
		def width = scaleArgs.width?.toInteger()
		def height = scaleArgs.height?.toInteger()
		
		if (width && height) {
			scale(image, width, height, bufferedImageType)
		} else if (width && !height) {
			scale(image, width, width, bufferedImageType)
		} else if (!width && height) {
			scale(image, height, height, bufferedImageType)
		} else {
			throw new IllegalStateException("Unhandled scale height/width combination")
		}
	}
	
	protected resize(image, Map resizeArgs, bufferedImageType) {
		def width = resizeArgs.width?.toInteger()
		def height = resizeArgs.height?.toInteger()
		
		if (width && height) {
			resize(image, width, height, bufferedImageType)
		} else if (width && !height) {
			height = (image.height * (width / image.width)).toInteger()
			resize(image, width, height, bufferedImageType)
		} else if (!width && height) {
			width = (image.width * (height / image.height)).toInteger()
			resize(image, width, height, bufferedImageType)
		} else {
			throw new IllegalStateException("Unhandled resize height/width combination")
		}
	}
	
	protected resize(image, width, height, bufferedImageType) {
		def widthScale = width / image.width
		def heightScale = height / image.height
		
		doScaleTransform(image, width, height, widthScale, heightScale, bufferedImageType)
	}
	
	protected scale(image, widthScale, heightScale, bufferedImageType) {
		def width = image.width * widthScale
		def height = image.height * heightScale
		
		doScaleTransform(image, width, height, widthScale, heightScale, bufferedImageType)
	}
	
	protected doScaleTransform(image, width, height, widthScale, heightScale, bufferedImageType) {
		def scaled = new BufferedImage(width, height, bufferedImageType)
		
		def graphics = scaled.createGraphics()
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
 		def transform = AffineTransform.getScaleInstance(widthScale, heightScale)
		graphics.drawRenderedImage(image, transform)
		graphics.dispose()
		
		scaled
	}
	
}
