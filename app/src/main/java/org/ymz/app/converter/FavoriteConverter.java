package org.ymz.app.converter;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.ymz.app.model.dto.favorite.FavoriteVO;
import org.ymz.app.model.entity.Favorite;

/**
 * 收藏夹转换器。
 *
 * @author ymz
 */
@Mapper(componentModel = "spring")
public interface FavoriteConverter {

    @Mapping(target = "appCount", ignore = true)
    FavoriteVO toFavoriteVO(Favorite favorite);
}
